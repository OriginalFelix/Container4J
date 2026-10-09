package systems.grebe.container4j.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Startet die CLI einer Laufzeit ohne Shell (Argumente werden nicht interpretiert) mit Timeout. Standardausgabe und
 * Fehlerausgabe werden zusammengeführt und als UTF-8 gelesen.
 */
public final class ProcessRunner {

    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private ProcessRunner() {
    }

    public record Result(List<String> command, int exitCode, boolean timedOut, String output) {

        public boolean ok() {
            return !timedOut && exitCode == 0;
        }

        /** Wirft mit verständlicher Meldung, falls der Aufruf fehlgeschlagen ist. */
        public Result orThrow(String what) {
            if (timedOut) {
                throw new IllegalStateException(what + ": Zeitüberschreitung");
            }
            if (exitCode != 0) {
                throw new IllegalStateException(what + " fehlgeschlagen (Exit-Code " + exitCode + "): "
                        + limitLines(output.strip(), 30));
            }
            return this;
        }
    }

    /** Führt {@code command} aus; {@code workDir} {@code null} = aktuelles Verzeichnis. */
    public static Result run(List<String> command, Duration timeout, Path workDir) {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("Programm nicht startbar: " + command.getFirst() + " (" + e.getMessage() + ")", e);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> copy(p.getInputStream(), out));
        try {
            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
            }
            reader.join(Duration.ofSeconds(5));
            String text;
            synchronized (out) {
                text = out.toString(StandardCharsets.UTF_8);
            }
            return new Result(command, finished ? p.exitValue() : -1, !finished, text);
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Abgebrochen", e);
        }
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[8192];
        try (in) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                synchronized (out) {
                    if (out.size() < MAX_BYTES) {
                        out.write(buf, 0, n);
                    }
                }
            }
        } catch (IOException ignored) {
            // Prozessende
        }
    }

    static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int i = s.indexOf('\n');
        return (i < 0 ? s : s.substring(0, i)).trim();
    }

    static String limitLines(String text, int maxLines) {
        if (text == null) {
            return "";
        }
        String[] lines = text.split("\\R", -1);
        if (lines.length <= maxLines) {
            return String.join("\n", lines);
        }
        return String.join("\n", List.of(lines).subList(0, maxLines))
                + "\n… [gekürzt: " + (lines.length - maxLines) + " weitere Zeilen]";
    }
}
