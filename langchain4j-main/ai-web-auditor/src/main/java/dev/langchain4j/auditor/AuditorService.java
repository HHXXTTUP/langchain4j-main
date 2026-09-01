package dev.langchain4j.auditor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.auditor.AuditModels.*;

final class AuditorService {
    private final AuditRunner runner;
    private final ReportWriter reportWriter;
    private final Path defaultOutput;
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    AuditorService(AuditRunner runner, ReportWriter reportWriter, Path defaultOutput) {
        this.runner = runner;
        this.reportWriter = reportWriter;
        this.defaultOutput = defaultOutput;
    }

    AuditResult run(AuditRequest request) throws Exception {
        validate(request);
        String id = UUID.randomUUID().toString();
        Instant started = Instant.now();
        Path directory = (request.outputDirectory == null || request.outputDirectory.isBlank()
                ? defaultOutput : Path.of(request.outputDirectory)).resolve(id);
        List<ModuleResult> modules = runner.run(id, request, directory.resolve("evidence"));
        AuditResult draft = new AuditResult(id, started.toString(), Instant.now().toString(), request.baseUrl,
                modules, null, null);
        ReportWriter.ReportFiles files = reportWriter.write(draft, directory);
        return new AuditResult(id, draft.startedAt(), draft.finishedAt(), draft.baseUrl(), modules,
                files.markdown(), files.html());
    }

    String toJson(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    AuditRequest fromJson(String value) throws Exception {
        return mapper.readValue(value, AuditRequest.class);
    }

    private static void validate(AuditRequest request) {
        if (request == null || request.baseUrl == null || request.baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (request.modules == null || request.modules.isEmpty()) {
            throw new IllegalArgumentException("at least one module is required");
        }
        for (ModuleConfig module : request.modules) {
            if (module.name == null || module.name.isBlank()) throw new IllegalArgumentException("module.name is required");
            if (module.path == null || module.path.isBlank()) module.path = "/";
        }
    }
}
