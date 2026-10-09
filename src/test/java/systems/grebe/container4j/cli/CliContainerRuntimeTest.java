package systems.grebe.container4j.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import systems.grebe.container4j.ContainerRuntime;
import systems.grebe.container4j.ContainerRuntime.RunSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CLI-Laufzeit gegen ein Shell-Skript, das sich als docker ausgibt: protokolliert die Argumente und liefert feste
 * Ausgaben. Prüft Kommandozeilen und das Parsen der Ausgaben ohne echte Laufzeit.
 */
@DisabledOnOs(OS.WINDOWS)
class CliContainerRuntimeTest {

    @TempDir
    Path dir;

    Path log;
    DockerRuntime docker;

    @BeforeEach
    void setUp() throws Exception {
        log = dir.resolve("args.log");
        Path cli = dir.resolve("fake-docker");
        Files.writeString(cli, """
                #!/bin/sh
                echo "$*" >> '%s'
                # globale Optionen überspringen
                while [ "$1" = "--context" ] || [ "$1" = "-H" ]; do shift 2; done
                case "$1" in
                  version) echo "27.1.0" ;;
                  ps) printf 'abcdef0123456789\\tweb\\tnginx:alpine\\tRunning\\tUp 5 minutes\\t0.0.0.0:8080->80/tcp\\n' ;;
                  inspect) echo '[{"Id":"abc","Name":"/web"}]' ;;
                  images) printf 'nginx\\talpine\\tsha256:1b595815db66aaaa\\t70MB\\t3 weeks ago\\n<none>\\t<none>\\tdeadbeef\\t1MB\\t1 day ago\\n' ;;
                  network) printf 'bridge\\tbridge\\nhost\\thost\\n' ;;
                  run) echo "Pulling ..."; echo "0123456789abcdef0123" ;;
                  exec) echo "hallo"; exit 3 ;;
                  rm) echo "Error: No such container: $2" >&2; exit 1 ;;
                  *) echo "unbekannt: $*" ;;
                esac
                """.formatted(log));
        Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"));
        docker = new DockerRuntime(cli.toString(), "remote", "tcp://build01:2375");
    }

    private List<String> calls() throws Exception {
        return Files.readAllLines(log);
    }

    @Test
    void probeReadsVersionAndPassesGlobalOptions() throws Exception {
        assertThat(docker.probe()).isEqualTo(ContainerRuntime.Availability.ok("27.1.0"));
        assertThat(calls()).containsExactly("--context remote -H tcp://build01:2375 version --format {{.Server.Version}}");
    }

    @Test
    void listParsesRows() throws Exception {
        assertThat(docker.list(true)).containsExactly(new ContainerRuntime.ContainerSummary(
                "abcdef012345", "web", "nginx:alpine", "running", "Up 5 minutes", "0.0.0.0:8080->80/tcp"));
        assertThat(calls().getFirst()).contains(" ps -a --no-trunc --format ");
    }

    @Test
    void inspectUnwrapsArray() {
        assertThat(docker.inspect("web")).isEqualTo("{\"Id\":\"abc\",\"Name\":\"/web\"}");
    }

    @Test
    void imagesAndNetworks() {
        assertThat(docker.images()).extracting(ContainerRuntime.ImageInfo::reference, ContainerRuntime.ImageInfo::id)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("nginx:alpine", "1b595815db66"),
                        org.assertj.core.groups.Tuple.tuple("<none>", "deadbeef"));
        assertThat(docker.networks()).extracting(ContainerRuntime.NamedResource::name).containsExactly("bridge", "host");
    }

    @Test
    void runBuildsCommandLineAndReturnsShortId() throws Exception {
        String id = docker.run(new RunSpec("nginx:alpine", "web", List.of("A=1"), List.of("127.0.0.1:8080:80"),
                List.of("data:/data"), "net", List.of("own=true"), List.of("nginx", "-g", "daemon off;")));
        assertThat(id).isEqualTo("0123456789ab");
        assertThat(calls()).containsExactly("--context remote -H tcp://build01:2375 run -d --name web -e A=1 "
                + "-p 127.0.0.1:8080:80 -v data:/data --label own=true --network net nginx:alpine nginx -g daemon off;");
    }

    @Test
    void execReturnsExitCodeAndOutput() {
        var r = docker.exec("web", List.of("cat", "/etc/hosts"), "/tmp", "root", Duration.ofSeconds(10));
        assertThat(r.exitCode()).isEqualTo(3);
        assertThat(r.ok()).isFalse();
        assertThat(r.output()).isEqualTo("hallo\n");
    }

    @Test
    void failingCommandThrowsWithOutput() {
        assertThatThrownBy(() -> docker.remove("gibtsnicht", true, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("docker rm fehlgeschlagen (Exit-Code 1)")
                .hasMessageContaining("No such container");
    }

    @Test
    void missingBinaryIsUnavailable() {
        var a = new PodmanRuntime(dir.resolve("gibtsnicht").toString(), null, null).probe();
        assertThat(a.available()).isFalse();
        assertThat(a.message()).contains("nicht startbar");
    }

    @Test
    void defaultsUseBinaryFromPath() {
        assertThat(new DockerRuntime().binary()).isEqualTo("docker");
        assertThat(new PodmanRuntime(" ", null, null).binary()).isEqualTo("podman");
        assertThat(new PodmanRuntime().id()).isEqualTo("podman");
    }
}
