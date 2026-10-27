package com.staytrack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks over the real Spring context: signup -> OTP -> login -> me,
 * tenant submits a request, and role-based security on /api/admin/**.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFixtures.class)
class StayTrackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signupOtpLoginAndMe() throws Exception {
        String email = "newtenant@example.com";

        // 1) Sign up -> receives a dev OTP (no SMTP in tests)
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"New","lastName":"Tenant","email":"%s",
                     "phone":"+639000000000","password":"Secret123"}
                    """.formatted(email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.requiresVerification").value(true))
            .andReturn();

        String devOtp = read(signup).get("devOtp").asText();
        assertNotNull(devOtp, "dev OTP should be returned in development mode");
        assertEquals(6, devOtp.length());

        // 2) Logging in before verification must fail with EMAIL_NOT_VERIFIED
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.details.code").value("EMAIL_NOT_VERIFIED"));

        // 3) A wrong code is rejected
        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"000000\"}"))
            .andExpect(status().isBadRequest());

        // 4) Correct code verifies the account and returns a token
        MvcResult verified = mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + devOtp + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andReturn();

        String token = read(verified).get("token").asText();

        // 5) Login now works
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists());

        // 6) /me with the token
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.status").value("active"));

        // 7) Without a token -> 401
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tenantCanSubmitRequestButNotAccessAdmin() throws Exception {
        String token = login("alex@staytrack.local", "Tenant@123");

        // Submit a maintenance request (no photo -> exercises the multipart handler)
        mockMvc.perform(multipart("/api/requests")
                .param("type", "maintenance")
                .param("category", "Internet / Wi-Fi")
                .param("description", "Wi-Fi keeps dropping in room 304.")
                .param("isAnonymous", "false")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.request.category").value("Internet / Wi-Fi"))
            .andExpect(jsonPath("$.request.status").value("pending"));

        // The tenant can list their own requests
        mockMvc.perform(get("/api/requests").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requests").isArray());

        // ...but must not reach admin endpoints
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanManageRequestsPaymentsAndTenants() throws Exception {
        String adminToken = login("admin@staytrack.local", "Admin@12345");

        // Dashboard
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenants.total").exists())
            .andExpect(jsonPath("$.payments.outstanding").exists());

        // Tenant list
        MvcResult tenants = mockMvc.perform(get("/api/admin/tenants").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenants").isArray())
            .andReturn();
        JsonNode tenantList = read(tenants).get("tenants");
        assertTrue(tenantList.size() >= 1);
        String tenantId = tenantList.get(0).get("id").asText();

        // Set that tenant's status to active
        mockMvc.perform(patch("/api/admin/tenants/" + tenantId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"active\"}"))
            .andExpect(status().isOk());

        // Generate monthly bills
        mockMvc.perform(post("/api/admin/payments/generate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"billingPeriod\":\"2026-10-01\",\"dueDate\":\"2026-10-10\","
                    + "\"utilitiesAmount\":50,\"otherAmount\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.generated").isNumber());

        // Payments list
        mockMvc.perform(get("/api/admin/payments").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payments").isArray());

        // Errors are JSON: a bad tenant id -> 400
        mockMvc.perform(get("/api/admin/tenants/not-a-uuid").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        // Audit trail has entries
        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logs").isArray());
    }

    @Test
    void adminCanCreateSingleBillForOneTenant() throws Exception {
        String adminToken = login("admin@staytrack.local", "Admin@12345");

        MvcResult tenants = mockMvc.perform(get("/api/admin/tenants")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        String tenantId = read(tenants).get("tenants").get(0).get("id").asText();

        String body = "{\"tenantId\":\"" + tenantId + "\",\"billingPeriod\":\"2026-11-01\","
            + "\"dueDate\":\"2026-11-10\",\"utilitiesAmount\":50,\"otherAmount\":0}";

        // Create the bill -> 201 with a computed total
        mockMvc.perform(post("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.payment.totalAmount").exists())
            .andExpect(jsonPath("$.payment.status").value("unpaid"));

        // The same tenant + period again is rejected
        mockMvc.perform(post("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void adminNoteIsVisibleToTheTenant() throws Exception {
        String tenantToken = login("alex@staytrack.local", "Tenant@123");

        // Tenant submits a request
        MvcResult created = mockMvc.perform(multipart("/api/requests")
                .param("type", "maintenance")
                .param("category", "Pest Control")
                .param("description", "Ants near the window.")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isCreated())
            .andReturn();
        String requestId = read(created).get("request").get("id").asText();

        // Admin adds a note
        String adminToken = login("admin@staytrack.local", "Admin@12345");
        mockMvc.perform(patch("/api/admin/requests/" + requestId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"in_progress\",\"adminNotes\":\"Pest control booked for Friday.\"}"))
            .andExpect(status().isOk());

        // The tenant can see that note on their own request list
        MvcResult list = mockMvc.perform(get("/api/requests")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode found = null;
        for (JsonNode request : read(list).get("requests")) {
            if (request.get("id").asText().equals(requestId)) {
                found = request;
            }
        }
        assertNotNull(found, "the submitted request should be listed for the tenant");
        assertEquals("Pest control booked for Friday.", found.get("adminNotes").asText());
        assertEquals("in_progress", found.get("status").asText());
    }

    @Test
    void adminCanCreateRoomsWithFloorAndDeleteEmptyOnes() throws Exception {
        String adminToken = login("admin@staytrack.local", "Admin@12345");

        // Create a room with an explicit floor and capacity
        MvcResult created = mockMvc.perform(post("/api/admin/rooms")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomNumber\":\"T-99\",\"building\":\"Test Hall\",\"floor\":7,"
                    + "\"monthlyRent\":250,\"capacity\":2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.room.floor").value(7))
            .andExpect(jsonPath("$.room.capacity").value(2))
            .andReturn();
        String newRoomId = read(created).get("room").get("id").asText();

        // An empty room can be deleted
        mockMvc.perform(delete("/api/admin/rooms/" + newRoomId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        // A room that still has a tenant assigned cannot be deleted
        MvcResult rooms = mockMvc.perform(get("/api/admin/rooms")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        String occupiedRoomId = null;
        for (JsonNode room : read(rooms).get("rooms")) {
            if (room.get("occupants").asInt() > 0) {
                occupiedRoomId = room.get("id").asText();
                break;
            }
        }
        assertNotNull(occupiedRoomId, "the seeder assigns a tenant to a room");
        mockMvc.perform(delete("/api/admin/rooms/" + occupiedRoomId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict());
    }

    @Test
    void signupRequiresPhoneNumber() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"No\",\"lastName\":\"Phone\","
                    + "\"email\":\"nophone@example.com\",\"password\":\"Secret123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.phone").exists());
    }

    @Test
    void paymentProofRequiresReferenceNumber() throws Exception {
        String tenantToken = login("alex@staytrack.local", "Tenant@123");

        MvcResult list = mockMvc.perform(get("/api/payments")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andReturn();

        String paymentId = null;
        for (JsonNode payment : read(list).get("payments")) {
            if (!"verified".equals(payment.get("status").asText())) {
                paymentId = payment.get("id").asText();
                break;
            }
        }
        assertNotNull(paymentId, "the seeder creates an unpaid bill");

        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0};
        MockMultipartFile proof = new MockMultipartFile("proof", "proof.png", "image/png", png);

        // No reference number -> rejected
        mockMvc.perform(multipart("/api/payments/" + paymentId + "/proof")
                .file(proof)
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isBadRequest());

        // With a reference number -> accepted
        mockMvc.perform(multipart("/api/payments/" + paymentId + "/proof")
                .file(proof)
                .param("referenceNo", "GCASH-123456")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payment.referenceNo").value("GCASH-123456"))
            .andExpect(jsonPath("$.payment.status").value("submitted"));

        // ...and the admin console list exposes that reference number
        String adminToken = login("admin@staytrack.local", "Admin@12345");
        MvcResult adminList = mockMvc.perform(get("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        String referenceSeen = null;
        for (JsonNode payment : read(adminList).get("payments")) {
            if (payment.get("id").asText().equals(paymentId)) {
                referenceSeen = payment.get("referenceNo").asText();
            }
        }
        assertEquals("GCASH-123456", referenceSeen,
            "the admin payment list should show the tenant's reference number");
    }

    @Test
    void tenantCanDeleteOnlyResolvedRequests() throws Exception {
        String tenantToken = login("alex@staytrack.local", "Tenant@123");

        MvcResult created = mockMvc.perform(multipart("/api/requests")
                .param("type", "maintenance")
                .param("category", "Furniture / Fixtures")
                .param("description", "Chair leg is broken.")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isCreated())
            .andReturn();
        String requestId = read(created).get("request").get("id").asText();

        // Still pending -> cannot delete
        mockMvc.perform(delete("/api/requests/" + requestId)
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isBadRequest());

        // Admin resolves it
        String adminToken = login("admin@staytrack.local", "Admin@12345");
        mockMvc.perform(patch("/api/admin/requests/" + requestId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"resolved\"}"))
            .andExpect(status().isOk());

        // Now the tenant can remove it
        mockMvc.perform(delete("/api/requests/" + requestId)
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/" + requestId)
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminCanPermanentlyDeleteATenant() throws Exception {
        String adminToken = login("admin@staytrack.local", "Admin@12345");

        MvcResult created = mockMvc.perform(post("/api/admin/tenants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Temp\",\"lastName\":\"Tenant\","
                    + "\"email\":\"temp.tenant@example.com\",\"phone\":\"+639111111111\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String tenantId = read(created).get("tenant").get("id").asText();

        // Give the tenant a bill so we can prove related rows are removed too
        MvcResult bill = mockMvc.perform(post("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"billingPeriod\":\"2026-12-01\","
                    + "\"dueDate\":\"2026-12-10\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String paymentId = read(bill).get("payment").get("id").asText();

        mockMvc.perform(delete("/api/admin/tenants/" + tenantId + "/permanent")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        MvcResult list = mockMvc.perform(get("/api/admin/tenants")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        boolean stillListed = false;
        for (JsonNode tenant : read(list).get("tenants")) {
            if (tenant.get("id").asText().equals(tenantId)) {
                stillListed = true;
            }
        }
        assertFalse(stillListed, "the deleted tenant should no longer be listed");

        // Their bill must be gone as well
        MvcResult payments = mockMvc.perform(get("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        boolean paymentStillThere = false;
        for (JsonNode payment : read(payments).get("payments")) {
            if (payment.get("id").asText().equals(paymentId)) {
                paymentStillThere = true;
            }
        }
        assertFalse(paymentStillThere, "related payment records should be deleted with the tenant");
    }

    @Test
    void adminCanSetMoveInDateOnEdit() throws Exception {
        String adminToken = login("admin@staytrack.local", "Admin@12345");

        MvcResult tenants = mockMvc.perform(get("/api/admin/tenants")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        String tenantId = read(tenants).get("tenants").get(0).get("id").asText();

        // Edit the tenant and set a move-in date only
        mockMvc.perform(patch("/api/admin/tenants/" + tenantId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"moveInDate\":\"2026-09-15\"}"))
            .andExpect(status().isOk());

        // The detail popup reads it back
        mockMvc.perform(get("/api/admin/tenants/" + tenantId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenant.moveInDate").value("2026-09-15"));
    }

    @Test
    void adminCanSendARentReminderForOneBill() throws Exception {
        String adminToken = login("admin@staytrack.local", "Admin@12345");

        // A fresh tenant + bill so this test does not depend on test order
        MvcResult created = mockMvc.perform(post("/api/admin/tenants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Remind\",\"lastName\":\"Me\","
                    + "\"email\":\"remind.me@example.com\",\"phone\":\"+639222222222\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String tenantId = read(created).get("tenant").get("id").asText();

        MvcResult bill = mockMvc.perform(post("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"billingPeriod\":\"2026-11-01\","
                    + "\"dueDate\":\"2026-11-10\",\"rentAmount\":300,\"utilitiesAmount\":50}"))
            .andExpect(status().isCreated())
            .andReturn();
        String paymentId = read(bill).get("payment").get("id").asText();

        // SMTP is not configured in tests, so this falls back to the console — still a 200
        mockMvc.perform(post("/api/admin/payments/" + paymentId + "/remind")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void tenantCanUpdateProfileAndPhoto() throws Exception {
        String tenantToken = login("alex@staytrack.local", "Tenant@123");

        // Edit name + phone
        mockMvc.perform(patch("/api/tenant/profile")
                .header("Authorization", "Bearer " + tenantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Alex\",\"lastName\":\"Rivera\","
                    + "\"phone\":\"+639998887777\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.phone").value("+639998887777"));

        // Upload a profile photo
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0};
        MockMultipartFile avatar = new MockMultipartFile("avatar", "me.png", "image/png", png);
        mockMvc.perform(multipart("/api/tenant/avatar")
                .file(avatar)
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl").exists());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.avatarUrl").exists());
    }

    @Test
    void adminCanDeleteOnlyVerifiedPayments() throws Exception {
        String adminToken = login("admin@staytrack.local", "Admin@12345");

        MvcResult created = mockMvc.perform(post("/api/admin/tenants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Pay\",\"lastName\":\"Delete\","
                    + "\"email\":\"pay.delete@example.com\",\"phone\":\"+639333333333\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String tenantId = read(created).get("tenant").get("id").asText();

        MvcResult bill = mockMvc.perform(post("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"billingPeriod\":\"2026-12-01\","
                    + "\"dueDate\":\"2026-12-10\",\"rentAmount\":400,\"utilitiesAmount\":0}"))
            .andExpect(status().isCreated())
            .andReturn();
        String paymentId = read(bill).get("payment").get("id").asText();

        // An unpaid bill must not be deletable
        mockMvc.perform(delete("/api/admin/payments/" + paymentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());

        // Approve it, then deletion is allowed
        mockMvc.perform(patch("/api/admin/payments/" + paymentId + "/verify")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/payments/" + paymentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        MvcResult list = mockMvc.perform(get("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        boolean stillThere = false;
        for (JsonNode payment : read(list).get("payments")) {
            if (payment.get("id").asText().equals(paymentId)) {
                stillThere = true;
            }
        }
        assertFalse(stillThere, "the deleted payment should no longer be listed");
    }

    @Test
    void submittingProofKeepsTheBillOwedUntilVerified() throws Exception {
        String tenantToken = login("alex@staytrack.local", "Tenant@123");
        String paymentId = currentBillId(tenantToken);

        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0};
        MockMultipartFile proof = new MockMultipartFile("proof", "proof.png", "image/png", png);
        mockMvc.perform(multipart("/api/payments/" + paymentId + "/proof")
                .file(proof)
                .param("referenceNo", "GCASH-UNDER-REVIEW")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payment.status").value("submitted"));

        // It must NOT report "all caught up" while the proof awaits review 
        MvcResult after = mockMvc.perform(get("/api/payments")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextDue").exists())
            .andExpect(jsonPath("$.nextDue.id").value(paymentId))
            .andExpect(jsonPath("$.nextDue.status").value("submitted"))
            .andReturn();

        JsonNode summary = read(after).get("summary");
        assertTrue(summary.get("outstanding").asDouble() > 0,
            "a payment awaiting verification is still owed");
    }

    @Test
    void tenantSeesTheRejectionReason() throws Exception {
        String tenantToken = login("alex@staytrack.local", "Tenant@123");
        String paymentId = currentBillId(tenantToken);

        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0};
        MockMultipartFile proof = new MockMultipartFile("proof", "proof.png", "image/png", png);
        mockMvc.perform(multipart("/api/payments/" + paymentId + "/proof")
                .file(proof)
                .param("referenceNo", "GCASH-REJECT-ME")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk());

        String adminToken = login("admin@staytrack.local", "Admin@12345");
        mockMvc.perform(patch("/api/admin/payments/" + paymentId + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remarks\":\"Receipt is unreadable\"}"))
            .andExpect(status().isOk());

        MvcResult after = mockMvc.perform(get("/api/payments")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = read(after);

        // The tenant can read the reason back
        String status = null;
        String remarks = null;
        for (JsonNode payment : body.get("payments")) {
            if (payment.get("id").asText().equals(paymentId)) {
                status = payment.get("status").asText();
                remarks = payment.get("remarks").isNull() ? null : payment.get("remarks").asText();
            }
        }
        assertEquals("rejected", status);
        assertEquals("Receipt is unreadable", remarks);

        // ...and the rejected bill is still owed, shown as the current bill with the reason
        assertEquals(paymentId, body.get("nextDue").get("id").asText());
        assertEquals("rejected", body.get("nextDue").get("status").asText());
        assertEquals("Receipt is unreadable", body.get("nextDue").get("remarks").asText());
    }

    @Test
    void tenantSeesTheNoteAddedWhenAdminVerifies() throws Exception {
        String adminToken = login("admin@staytrack.local", "Admin@12345");
        String tenantId = tenantIdByEmail(adminToken, "alex@staytrack.local");

        // A dedicated future bill, so this test never disturbs the tenant's current bill
        MvcResult bill = mockMvc.perform(post("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"billingPeriod\":\"2027-01-01\","
                    + "\"dueDate\":\"2027-01-10\",\"rentAmount\":300}"))
            .andExpect(status().isCreated())
            .andReturn();
        String paymentId = read(bill).get("payment").get("id").asText();

        // Admin approves it WITH a note
        mockMvc.perform(patch("/api/admin/payments/" + paymentId + "/verify")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remarks\":\"Thank you, get well soon!\"}"))
            .andExpect(status().isOk());

        // The tenant can read that note back on their own bill
        String tenantToken = login("alex@staytrack.local", "Tenant@123");
        MvcResult after = mockMvc.perform(get("/api/payments")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andReturn();

        String status = null;
        String remarks = null;
        for (JsonNode payment : read(after).get("payments")) {
            if (payment.get("id").asText().equals(paymentId)) {
                status = payment.get("status").asText();
                remarks = payment.get("remarks").isNull() ? null : payment.get("remarks").asText();
            }
        }
        assertEquals("verified", status);
        assertEquals("Thank you, get well soon!", remarks,
            "the note added while verifying must be visible to the tenant");
    }

    @Test
    void adminCanDeleteOnlyResolvedRequests() throws Exception {
        String tenantToken = login("alex@staytrack.local", "Tenant@123");

        MvcResult created = mockMvc.perform(multipart("/api/requests")
                .param("type", "neighbor")
                .param("category", "Noise Complaint")
                .param("description", "Loud music after midnight.")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isCreated())
            .andReturn();
        String requestId = read(created).get("request").get("id").asText();

        String adminToken = login("admin@staytrack.local", "Admin@12345");

        // Still pending -> the admin must not be able to delete it
        mockMvc.perform(delete("/api/admin/requests/" + requestId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());

        // Resolve it first
        mockMvc.perform(patch("/api/admin/requests/" + requestId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"resolved\"}"))
            .andExpect(status().isOk());

        // Now deletion is allowed
        mockMvc.perform(delete("/api/admin/requests/" + requestId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        // And it is gone from the admin list
        MvcResult list = mockMvc.perform(get("/api/admin/requests")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        boolean stillListed = false;
        for (JsonNode request : read(list).get("requests")) {
            if (request.get("id").asText().equals(requestId)) {
                stillListed = true;
            }
        }
        assertFalse(stillListed, "the deleted request should no longer be listed");
    }

    @Test
    void tenantCanDeleteOnlyTheirOwnVerifiedRecords() throws Exception {
        String adminToken = login("admin@staytrack.local", "Admin@12345");
        String tenantId = tenantIdByEmail(adminToken, "alex@staytrack.local");

        MvcResult bill = mockMvc.perform(post("/api/admin/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"billingPeriod\":\"2027-02-01\","
                    + "\"dueDate\":\"2027-02-10\",\"rentAmount\":300}"))
            .andExpect(status().isCreated())
            .andReturn();
        String paymentId = read(bill).get("payment").get("id").asText();

        String tenantToken = login("alex@staytrack.local", "Tenant@123");

        // Unpaid -> the tenant must not be able to delete it
        mockMvc.perform(delete("/api/payments/" + paymentId)
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isBadRequest());

        // Admin verifies it
        mockMvc.perform(patch("/api/admin/payments/" + paymentId + "/verify")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        // Now the tenant can remove their own settled record
        mockMvc.perform(delete("/api/payments/" + paymentId)
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk());

        MvcResult after = mockMvc.perform(get("/api/payments")
                .header("Authorization", "Bearer " + tenantToken))
            .andExpect(status().isOk())
            .andReturn();
        boolean stillThere = false;
        for (JsonNode payment : read(after).get("payments")) {
            if (payment.get("id").asText().equals(paymentId)) {
                stillThere = true;
            }
        }
        assertFalse(stillThere, "the deleted payment record should no longer be listed");
    }

    // ------------------------------------------------------------------
    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return read(result).get("token").asText();
    }

    private String tenantIdByEmail(String adminToken, String email) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/tenants")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        for (JsonNode tenant : read(result).get("tenants")) {
            if (email.equalsIgnoreCase(tenant.get("email").asText())) {
                return tenant.get("id").asText();
            }
        }
        throw new IllegalStateException("tenant not found: " + email);
    }

    /** The bill the tenant currently sees as "next due". */
    private String currentBillId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/payments")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextDue").exists())
            .andReturn();
        return read(result).get("nextDue").get("id").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
