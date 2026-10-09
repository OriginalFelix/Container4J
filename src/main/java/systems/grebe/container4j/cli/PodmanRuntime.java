package systems.grebe.container4j.cli;

import java.util.ArrayList;
import java.util.List;

/** Podman (lokal, Podman-Machine unter Windows/macOS oder Remote-Verbindung). */
public final class PodmanRuntime extends CliContainerRuntime {

    public static final String ID = "podman";

    /** {@code podman} aus dem PATH mit der Standardverbindung. */
    public PodmanRuntime() {
        this(ID, null, null);
    }

    /**
     * @param binary     Pfad zur podman-CLI oder Name im PATH ({@code null} = {@code podman})
     * @param connection optional {@code --connection}, {@code null} = Standardverbindung
     * @param url        optional {@code --url}, z.B. {@code ssh://user@host/run/podman/podman.sock}
     */
    public PodmanRuntime(String binary, String connection, String url) {
        super(ID, blank(binary) ? ID : binary, globalArgs(connection, url));
    }

    private static List<String> globalArgs(String connection, String url) {
        List<String> global = new ArrayList<>();
        if (!blank(connection)) {
            global.addAll(List.of("--connection", connection));
        }
        if (!blank(url)) {
            global.addAll(List.of("--url", url));
        }
        return global;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
