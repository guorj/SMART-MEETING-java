package com.smartmeeting.asr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.MeetingIsvProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class XfyunIsvClientIdentifyTest {

    private MeetingIsvProperties isvProperties;
    private XfyunIsvClient client;

    @BeforeEach
    void setUp() {
        isvProperties = new MeetingIsvProperties();
        isvProperties.setMatchScoreThreshold(0.6);
        isvProperties.setSearchTopKMax(10);
        isvProperties.setSearchTopKMin(3);
        client = spy(new XfyunIsvClient(new ObjectMapper(), isvProperties));
    }

    @Test
    @DisplayName("临时列席 featureId 不在候选列表时仍应命中")
    void identifyAmongCandidates_doesNotFilterByCandidates() {
        doReturn(List.of(score("feat-guest", 0.85), score("feat-a", 0.7)))
                .when(client).search1N(any(), any(), anyInt());

        Optional<XfyunIsvClient.IdentifyResult> result =
                client.identifyAmongCandidates(new byte[2000], List.of("feat-a"), 10);

        assertThat(result).isPresent();
        assertThat(result.get().featureId()).isEqualTo("feat-guest");
        assertThat(result.get().score()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("最高分低于阈值时返回 empty")
    void identifyAmongCandidates_belowThreshold_returnsEmpty() {
        doReturn(List.of(score("feat-a", 0.5)))
                .when(client).search1N(any(), any(), anyInt());

        Optional<XfyunIsvClient.IdentifyResult> result =
                client.identifyAmongCandidates(new byte[2000], null, 10);

        assertThat(result).isEmpty();
    }

    private static XfyunIsvClient.SearchScoreItem score(String featureId, double score) {
        XfyunIsvClient.SearchScoreItem item = new XfyunIsvClient.SearchScoreItem();
        item.featureId = featureId;
        item.score = score;
        return item;
    }
}
