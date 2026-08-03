package com.streamsocial.dashboard.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Doesn't need Docker or Kafka - asserts the static dashboard page
 * actually renders the real panel markup this lesson defines, not just
 * a bare 200 OK. The live-data path itself is proven separately in
 * {@link UserActionsFeedBroadcastIT}, since that needs a real broker.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardPageSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void indexPageRendersTheUserActionsPanel() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("StreamSocial")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("panel-user-actions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("user-actions-feed")));
    }

    @Test
    void indexPageRendersTheContentInteractionsPanel() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("panel-content-interactions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("content-interactions-feed")));
    }
}
