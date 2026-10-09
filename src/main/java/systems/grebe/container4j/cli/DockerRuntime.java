package systems.grebe.container4j.cli;

import java.util.ArrayList;
import java.util.List;

/** Docker (Docker Desktop, Docker Engine, Remote-Daemons über Kontext bzw. Host). */
public final class DockerRuntime extends CliContainerRuntime {

    public static final String ID = "docker";

    /** {@code docker} aus dem PATH mit dem aktuellen Kontext. */
    public DockerRuntime() {
        this(ID, null, null);
    }

    /**
     * @param binary  Pfad zur docker-CLI oder Name im PATH ({@code null} = {@code docker})
     * @param context optional {@code --context}, {@code null} = aktueller Kontext
     * @param host    optional {@code -H}, z.B. {@code tcp://build01:2375}
     */
    public DockerRuntime(String binary, String context, String host) {
        super(ID, blank(binary) ? ID : binary, globalArgs(context, host));
    }

    private static List<String> globalArgs(String context, String host) {
        List<String> global = new ArrayList<>();
        if (!blank(context)) {
            global.addAll(List.of("--context", context));
        }
        if (!blank(host)) {
            global.addAll(List.of("-H", host));
        }
        return global;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
