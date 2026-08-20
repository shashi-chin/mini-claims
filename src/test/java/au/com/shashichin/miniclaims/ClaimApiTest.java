package au.com.shashichin.miniclaims;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimApiTest {

    private static final String BODY =
            "{"
                    + "\"lossDate\":\"2026-08-20\","
                    + "\"description\":\"Rear-end collision at a roundabout\","
                    + "\"status\":\"draft\","
                    + "\"reporterName\":\"Shashi Chin\""
                    + "}";

    @Autowired
    private MockMvc mvc;

    @Test
    void missingIdempotencyKey_returns400() throws Exception {
        mvc.perform(post("/claims").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void firstPost_createsClaimWithClaimId() throws Exception {
        mvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "create-1")
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimId").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.lossDate").value("2026-08-20"))
                .andExpect(jsonPath("$.description").value("Rear-end collision at a roundabout"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.reporterName").value("Shashi Chin"));
    }

    @Test
    void replaySameKeyAndBody_returnsSameClaimId() throws Exception {
        String key = "replay-1";
        MvcResult first = mvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String claimId = JsonPath.read(first.getResponse().getContentAsString(), "$.claimId");

        mvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claimId));
    }

    @Test
    void sameKeyDifferentBody_returns409() throws Exception {
        String key = "conflict-1";
        mvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(BODY))
                .andExpect(status().isCreated());

        String otherBody =
                "{"
                        + "\"lossDate\":\"2026-08-21\","
                        + "\"description\":\"Hail damage to windscreen\","
                        + "\"status\":\"open\","
                        + "\"reporterName\":\"Shashi Chin\""
                        + "}";
        mvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(otherBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getCreatedClaim_returnsClaim() throws Exception {
        MvcResult created = mvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "get-1")
                        .content(BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String claimId = JsonPath.read(created.getResponse().getContentAsString(), "$.claimId");

        mvc.perform(get("/claims/{claimId}", claimId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andExpect(jsonPath("$.reporterName").value("Shashi Chin"))
                .andExpect(jsonPath("$.status").value("draft"));
    }

    @Test
    void openApiDocs_arePublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/claims']").exists());
    }
}
