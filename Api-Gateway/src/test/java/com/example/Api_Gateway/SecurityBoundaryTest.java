package com.example.Api_Gateway;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.Api_Gateway.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "eureka.client.enabled=false")
@ActiveProfiles("test")
class SecurityBoundaryTest {
  @Autowired WebApplicationContext context;
  @Autowired JwtService jwt;

  @Test
  void bank_shouldRejectAnonymousAndStudentTokensBeforeController() throws Exception {
    var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    mvc.perform(get("/question/getAllQuestion")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/question/getAllQuestion")
                .header(
                    "Authorization",
                    "Bearer " + jwt.generateToken("student@example.test", "student", "STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/question/getAllQuestion").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
  }
}
