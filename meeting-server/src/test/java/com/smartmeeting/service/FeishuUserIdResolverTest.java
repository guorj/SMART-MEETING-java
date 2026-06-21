package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.UserMapping;
import com.smartmeeting.repository.UserMappingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuUserIdResolverTest {

    @Mock
    private UserMappingMapper userMappingMapper;

    @Test
    void resolveParticipant_usesNameWhenVoiceprintPlaceholder() {
        FeishuUserIdResolver resolver = new FeishuUserIdResolver(userMappingMapper);
        UserMapping mapping = new UserMapping();
        mapping.setUserName("李金杰");
        mapping.setFeishuUserId("8813018f");
        when(userMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mapping);

        Optional<String> resolved = resolver.resolveParticipant("vp_abc123", "李金杰");

        assertTrue(resolved.isPresent());
        assertEquals("8813018f", resolved.get());
    }

    @Test
    void resolveByUserName_returnsEmptyWhenUnmapped() {
        FeishuUserIdResolver resolver = new FeishuUserIdResolver(userMappingMapper);
        when(userMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertTrue(resolver.resolveByUserName("不存在").isEmpty());
    }
}
