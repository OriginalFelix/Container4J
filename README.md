# Container4J

OCI-Container (Docker, Podman, …) aus Java steuern – über die Docker-kompatible Kommandozeile der jeweiligen Laufzeit.
Keine Abhängigkeiten zur Laufzeit, Java 21+.

```java
ContainerRuntime rt = new PodmanRuntime();            // oder new DockerRuntime()
if (rt.probe().available()) {
    rt.list(true).forEach(c -> System.out.println(c.name() + " " + c.state()));
    String id = rt.run(new ContainerRuntime.RunSpec("docker.io/library/nginx:alpine", "web",
            List.of(), List.of("127.0.0.1:8080:80"), List.of(), null, List.of(), List.of()));
    var r = rt.exec("web", List.of("cat", "/etc/nginx/nginx.conf"), null, null, Duration.ofSeconds(30));
    rt.remove("web", true, false);
}
```

| Bereich | Methoden |
|---|---|
| Lesen | `probe`, `list`, `inspect` (Docker-kompatibles JSON), `logs`, `stats`, `top`, `diff`, `images`, `networks`, `volumes` |
| Aktionen | `exec`, `start`, `stop`, `restart`, `copyFrom`, `copyTo`, `run`, `remove`, `pull`, `removeImage` |
| Compose | `supportsCompose`, `compose(projectDir, args, timeout)` |

Alle Methoden blockieren und werfen bei Fehlern eine `IllegalStateException` mit der (gekürzten) Ausgabe der CLI.
Zugriffsprüfungen (welche Container, Images oder Host-Pfade erlaubt sind) sind Sache der Anwendung.

## Laufzeiten

| Klasse | Programm | Globale Optionen |
|---|---|---|
| `DockerRuntime(binary, context, host)` | `docker` | `--context`, `-H` (z.B. `tcp://build01:2375`) |
| `PodmanRuntime(binary, connection, url)` | `podman` | `--connection`, `--url` (z.B. `ssh://user@host/run/podman/podman.sock`) |

Weitere Laufzeiten mit Docker-kompatibler CLI erweitern `CliContainerRuntime`:

```java
ContainerRuntime nerdctl = new CliContainerRuntime("nerdctl", "nerdctl", List.of()) { };
```

Laufzeiten ohne CLI (z.B. über eine REST-API) implementieren `ContainerRuntime` direkt.

## Bauen

```bash
./gradlew build                 # Jar, Quellen, Javadoc, Tests
./gradlew publishToMavenLocal   # systems.grebe:container4j:<version> ins lokale Maven-Repository
./gradlew publish -Pcontainer4jRepository=https://… -Pcontainer4jRepositoryUser=… -Pcontainer4jRepositoryPassword=…
```

Die Tests laufen gegen ein Shell-Skript, das sich als CLI ausgibt – keine echte Laufzeit nötig (unter Windows
übersprungen).
