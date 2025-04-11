package uk.gov.hmcts.reform.enforcement.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.reform.enforcement.model.SectionResponse;
import uk.gov.hmcts.reform.enforcement.model.Task;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Case Sections", description = "Static endpoints for each section of the case flow")
public class CaseController {

    @GetMapping("/{section}")
    @Operation(summary = "Get static content for a case section (e.g. claims, hearings, etc.)")
    public ResponseEntity<List<SectionResponse>> getSection(@PathVariable String section) {
        return ResponseEntity.ok(getMockDataForSection(section.toLowerCase()));
    }


    private List<SectionResponse> getMockDataForSection(String section) {
        return switch (section) {
            case "claims" -> mockClaimData();
            case "hearings" -> mockHearingData();
            case "responses", "additionalinfo", "orders", "judgements", "viewpdfs", "submitandpay", "applications" ->
                mockGenericData(section.toUpperCase(), "Task.AAA6." + capitalize(section) + ".View");
            default -> Collections.emptyList();
        };
    }

    private List<SectionResponse> mockClaimData() {
        SectionResponse res = new SectionResponse(
            "CLAIM", "AVAILABLE",
            new Task(
                "Task.AAA6.Claim.ViewClaim", Map.of(
                "dueDate", "2025-05-20",
                "amount", 76.00,
                "location", "London",
                "appointmentTime", "2025-05-20T10:30:00Z"
            )
            )
        );
        return List.of(res);
    }

    private List<SectionResponse> mockHearingData() {
        SectionResponse res = new SectionResponse(
            "HEARING", "ACTION_NEEDED",
            new Task(
                "Task.AAA6.Hearing.UploadDocuments", Map.of(
                "deadline", "2025-05-20"
            )
            )
        );
        return List.of(res);
    }

    private List<SectionResponse> mockGenericData(String groupId, String templateId) {
        SectionResponse res = new SectionResponse(
            groupId, "AVAILABLE",
            new Task(templateId, Map.of("info", "Static content for " + groupId.toLowerCase()))
        );
        return List.of(res);
    }

    private String capitalize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

}
