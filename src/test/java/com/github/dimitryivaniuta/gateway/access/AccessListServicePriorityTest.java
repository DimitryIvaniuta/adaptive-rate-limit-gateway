package com.github.dimitryivaniuta.gateway.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.gateway.domain.AccessListMode;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Verifies access-list priority rules.
 */
class AccessListServicePriorityTest {

    /**
     * BLOCK must win over ALLOW for emergency security response.
     */
    @Test
    @SuppressWarnings("unchecked")
    void blockWinsOverAllow() throws Exception {
        Method method = AccessListService.class.getDeclaredMethod("highest", Optional[].class);
        method.setAccessible(true);

        Optional<AccessListMode> result = (Optional<AccessListMode>) method.invoke(nullInstance(), (Object) new Optional[]{
                Optional.of(AccessListMode.ALLOW), Optional.of(AccessListMode.BLOCK)
        });

        assertThat(result).contains(AccessListMode.BLOCK);
    }

    private AccessListService nullInstance() {
        return new AccessListService(null, null, null);
    }
}
