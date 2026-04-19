# Tool System

## ToolResult Standard Response

All tools return structured JSON via the `ToolResult` record:

```json
{
  "status": "success",
  "summary": "File written successfully to: /tmp/file/test.txt",
  "errorMessage": null,
  "nextActions": ["Summarize the file content", "Search within the file"],
  "artifacts": ["/tmp/file/test.txt"]
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| status | String | `"success"`, `"error"`, or `"warning"` |
| summary | String | Human-readable result (null on error) |
| errorMessage | String | Error details (null on success) |
| nextActions | List\<String\> | Suggested follow-up actions |
| artifacts | List\<String\> | Output file paths or references |

### Factory Methods

```java
ToolResult.success("Operation completed")
ToolResult.error("File not found: missing.txt")
ToolResult.warning("Partial results only")
```

### Builder Methods

```java
ToolResult.success("PDF generated")
    .withArtifacts(List.of("/tmp/output/report.pdf"))
    .withNextActions(List.of("Read the PDF", "Download the PDF"))
    .toJson();
```

## Available Tools

<!-- AUTO-GENERATED:START -->
| Tool | Class | Description | Next Actions |
|------|-------|-------------|--------------|
| FileOperationTool | `FileOperationTool` | Read/write files | Summarize content, Search within file |
| WebSearchTool | `WebSearchTool` | Search via Baidu (SearchAPI) | Read result, Refine terms, Scrape page |
| WebScrapingTool | `WebScrapingTool` | Extract web page content | Extract info, Summarize page |
| PDFGenerationTool | `PDFGenerationTool` | Generate PDF with iText | Read PDF, Download PDF |
| ResourceDownloadTool | `ResourceDownloadTool` | Download files from URLs | — |
| ImageSearchTool | `ImageSearchTool` | Search images via Pexels API | Download image, Search more |
| TerminateTool | `TerminateTool` | End agent execution | — |
<!-- AUTO-GENERATED:END -->

## Tool Registration

Tools are registered as Spring beans in `ToolRegistration`:

```java
@Configuration
public class ToolRegistration {
    @Bean public FileOperationTool fileOperationTool();
    @Bean public PDFGenerationTool pdfGenerationTool();
    @Bean public ResourceDownloadTool resourceDownloadTool();
    @Bean public WebScrapingTool webScrapingTool();
    @Bean public WebSearchTool webSearchTool(@Value("${...}") String apiKey);
    @Bean public TerminateTool terminateTool();

    @Bean
    public ToolCallback[] allTools(...) {
        // Aggregates all tools via ToolCallbacks.from()
    }
}
```

The `allTools` bean is injected wherever tools are needed (agents, BookApp).

## Tool Security

`ToolSecurityUtils` provides:

- **Path traversal prevention**: `resolveSafePath(baseDir, fileName)` normalizes and validates paths stay within the base directory
- **URL validation**: `validatePublicHttpUrl(url)` rejects private IPs, non-HTTP schemes

Example:
```java
// Blocks: "../../etc/passwd", "../secret.txt"
Path safe = ToolSecurityUtils.resolveSafePath("/tmp/file", fileName);

// Blocks: "http://127.0.0.1:8080", "file:///etc/passwd"
URI safeUri = ToolSecurityUtils.validatePublicHttpUrl(url);
```

## Tool Output Examples

### FileOperationTool

**Read success:**
```json
{"status":"success","summary":"file content here","errorMessage":null,
 "nextActions":["Summarize the file content","Search within the file"],
 "artifacts":["/tmp/file/test.txt"]}
```

**Read error:**
```json
{"status":"error","summary":null,"errorMessage":"File not found: missing.txt",
 "nextActions":[],"artifacts":[]}
```

### WebSearchTool

**Search success:**
```json
{"status":"success","summary":"Found 5 results for: Java tutorial\n{...}",
 "errorMessage":null,
 "nextActions":["Read a specific result","Refine search terms","Scrape a result page"],
 "artifacts":[]}
```
