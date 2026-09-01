package dev.langchain4j.auditor;

import java.nio.file.Path;
import java.util.List;

import static dev.langchain4j.auditor.AuditModels.*;

interface AuditRunner {
    List<ModuleResult> run(String auditId, AuditRequest request, Path evidenceDirectory) throws Exception;
}
