package com.vypeensoft.dlnaserver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cycles through candidate DLNA protocolInfo parameter strings for video/mp4,
 * one per BROWSE METADATA click, to discover which profile the TV accepts.
 *
 * <p>Usage flow:
 * <ol>
 *   <li>On startup, call {@link #loadWinner()} to restore the previously found winner.</li>
 *   <li>Each time the TV clicks an MP4 (BROWSE METADATA), call {@link #advanceAndGet()} to get
 *       the next candidate protocolInfo parameter string.</li>
 *   <li>When the HTTP streaming server receives a real GET request (not HEAD) for that item,
 *       call {@link #notifySuccess()} to log the winning profile.</li>
 * </ol>
 *
 * <p>The winner is appended to {@value #WINNER_FILE} in the current working directory.
 * On the next launch it is restored as the starting candidate so the working profile is
 * used immediately without needing another cycle.
 */
public class DlnaProfileCycler {

    private static final Logger log = LoggerFactory.getLogger(DlnaProfileCycler.class);

    /** Log file written into the server's working directory when a winner is found. */
    public static final String WINNER_FILE = "dlna_profile_winner.log";

    /**
     * All candidate DLNA.ORG_ parameter strings (the 4th field of http-get:*:video/mp4:XXXXX).
     * Ordered from most-likely-to-work to least.
     *
     * Convention: empty string = bare wildcard, i.e. http-get:*:video/mp4:*
     */
    public static final List<String> CANDIDATES = Collections.unmodifiableList(Arrays.asList(
        // --- No PN: broadest fallback — try first ---
        "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",

        // --- HD profiles (LG NetCast primary decoders) ---
        "DLNA.ORG_PN=AVC_MP4_MP_HD_1080i_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
        "DLNA.ORG_PN=AVC_MP4_MP_HD_720p_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
        "DLNA.ORG_PN=AVC_MP4_BL_L3L_SD_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
        "DLNA.ORG_PN=AVC_MP4_MP_SD_AAC_MULT5;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
        "DLNA.ORG_PN=AVC_MP4_HP_HD_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
        "DLNA.ORG_PN=AVC_MP4_MP_SD_MPEG1_L3;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",

        // --- Same profiles with FLAGS=01500000 ---
        "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01500000000000000000000000000000",
        "DLNA.ORG_PN=AVC_MP4_MP_HD_1080i_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01500000000000000000000000000000",
        "DLNA.ORG_PN=AVC_MP4_MP_HD_720p_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01500000000000000000000000000000",
        "DLNA.ORG_PN=AVC_MP4_BL_L3L_SD_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01500000000000000000000000000000",

        // --- OP=00 (no seeking signalled) ---
        "DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
        "DLNA.ORG_PN=AVC_MP4_MP_HD_1080i_AAC;DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",

        // --- Bare wildcard: http-get:*:video/mp4:* ---
        ""
    ));

    private final AtomicInteger index = new AtomicInteger(0);
    private final AtomicBoolean winnerLogged = new AtomicBoolean(false);
    private volatile String activeCandidate = CANDIDATES.get(0);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Called on each BROWSE METADATA for a video/mp4 item (i.e. user clicked on the file).
     * Advances to the next candidate and returns the full protocolInfo string for video/mp4.
     */
    public String advanceAndGet() {
        int i = index.getAndIncrement() % CANDIDATES.size();
        activeCandidate = CANDIDATES.get(i);
        String proto = buildProtocolInfo(activeCandidate);
        log.info("[ProfileCycler] *** Trying candidate #{}/{}: [{}] → protocolInfo: {}",
                i + 1, CANDIDATES.size(), activeCandidate.isEmpty() ? "wildcard *" : activeCandidate, proto);
        // Reset winner-logged flag so we can detect the next success
        winnerLogged.set(false);
        return proto;
    }

    /**
     * Returns the current protocolInfo without advancing. Used by the HTTP server to know
     * what profile was agreed in the last BROWSE so it can mirror it in response headers.
     */
    public String getCurrentProtocolInfo() {
        return buildProtocolInfo(activeCandidate);
    }

    /**
     * Returns the current raw candidate string (the DLNA.ORG_* parameter part only).
     */
    public String getCurrentCandidate() {
        return activeCandidate;
    }

    /**
     * Called by the HTTP streaming server when a real GET request (not HEAD) arrives for
     * a video item — meaning the TV accepted the last advertised profile and is actually
     * streaming. Logs the winning profile to {@value #WINNER_FILE} exactly once per cycle.
     */
    public void notifySuccess() {
        if (winnerLogged.compareAndSet(false, true)) {
            String winner = activeCandidate;
            int idx = CANDIDATES.indexOf(winner);
            String winnerProto = buildProtocolInfo(winner);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String line = String.format(
                    "%s | WINNER #%d | candidate=[%s] | protocolInfo=[%s]",
                    timestamp, idx + 1,
                    winner.isEmpty() ? "wildcard *" : winner,
                    winnerProto);

            log.info("[ProfileCycler] *** SUCCESS! TV accepted profile. {}", line);
            log.info("[ProfileCycler] *** To make this permanent, set DLNA.ORG candidate #{} in source or properties.", idx + 1);

            appendToWinnerFile(line);
        }
    }

    /**
     * On startup: reads {@value #WINNER_FILE} and restores the last winning candidate as
     * the current (index=0) starting point so it is tried immediately on the first click.
     *
     * @return the restored candidate string, or {@code null} if no winner file exists.
     */
    public String loadWinner() {
        File f = new File(WINNER_FILE);
        if (!f.exists()) {
            log.info("[ProfileCycler] No winner file found ({}). Starting cycle from candidate #1.", WINNER_FILE);
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(f.toPath());
            // Walk from the end to find the most recent WINNER line
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (line.contains("| WINNER #") && line.contains("| candidate=[")) {
                    // Extract candidate between "candidate=[" and "]"
                    int start = line.indexOf("| candidate=[") + "| candidate=[".length();
                    int end = line.indexOf("]", start);
                    if (start > 0 && end > start) {
                        String saved = line.substring(start, end);
                        if ("wildcard *".equals(saved)) {
                            saved = "";
                        }
                        int idx = CANDIDATES.indexOf(saved);
                        if (idx >= 0) {
                            index.set(idx);
                            activeCandidate = saved;
                            log.info("[ProfileCycler] Restored winner from {}: candidate #{}=[{}]",
                                    WINNER_FILE, idx + 1, saved.isEmpty() ? "wildcard *" : saved);
                            return saved;
                        } else {
                            log.warn("[ProfileCycler] Saved winner '{}' not found in current candidate list. Ignoring.", saved);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[ProfileCycler] Could not read winner file {}: {}", WINNER_FILE, e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the full http-get protocolInfo string for video/mp4 from a raw candidate string.
     */
    private static String buildProtocolInfo(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return "http-get:*:video/mp4:*";
        }
        return "http-get:*:video/mp4:" + candidate;
    }

    private void appendToWinnerFile(String line) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(WINNER_FILE, true))) {
            pw.println(line);
            pw.flush();
            log.info("[ProfileCycler] Winner written to file: {}", new File(WINNER_FILE).getAbsolutePath());
        } catch (IOException e) {
            log.error("[ProfileCycler] Could not write winner file {}: {}", WINNER_FILE, e.getMessage());
        }
    }
}
