package com.pics2text.worker.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class LibvipsProcessRenderer {
    private final PrintRenderProperties properties;
    public LibvipsProcessRenderer(PrintRenderProperties properties) { this.properties = properties; }

    public Result render(Path specification, Path output, Path workingDirectory, Runnable heartbeat)
            throws IOException, InterruptedException {
        requireRegularFile(properties.pythonExecutable(), "Python执行程序");
        requireRegularFile(properties.rendererScript(), "libvips渲染脚本");
        List<String> command = List.of(properties.pythonExecutable().toString(),
                properties.rendererScript().toString(), specification.toString(), output.toString());
        Path logPath = workingDirectory.resolve("libvips-render.log");
        Process process = new ProcessBuilder(command).directory(workingDirectory.toFile())
                .redirectErrorStream(true).redirectOutput(logPath.toFile()).start();
        long deadline = System.nanoTime() + properties.timeout().toNanos();
        boolean finished = false;
        while (!finished && System.nanoTime() < deadline) {
            long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            finished = process.waitFor(Math.min(30_000, remainingMillis), TimeUnit.MILLISECONDS);
            if (!finished) heartbeat.run();
        }
        if (!finished) {
            process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS);
            throw new IOException("libvips渲染超时：" + properties.timeout());
        }
        String logs;
        try (var input = Files.newInputStream(logPath)) {
            logs = new String(input.readNBytes(32_768), StandardCharsets.UTF_8);
        }
        if (process.exitValue() != 0) throw new IOException("libvips渲染失败：" + logs);
        String version = logs.lines().filter(line -> line.startsWith("LIBVIPS_VERSION="))
                .map(line -> line.substring("LIBVIPS_VERSION=".length())).findFirst().orElse("unknown");
        return new Result(version, logs);
    }

    private void requireRegularFile(Path path, String name) throws IOException {
        if (path == null || !Files.isRegularFile(path)) throw new IOException(name + "不存在：" + path);
    }
    public record Result(String libvipsVersion, String logs) { }
}
