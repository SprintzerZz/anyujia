package com.anyujia.anyujiacommoncore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;

import com.anyujia.anyujiacommoncore.utils.BeanCopyUtil;
import org.junit.jupiter.api.Test;

public class BeanCopyUtilTests {

    @Test
    void copyListPropertiesShouldCopySameNameFields() {
        List<UserEntity> users = List.of(new UserEntity(1L, "anyujia"), new UserEntity(2L, "gateway"));

        List<UserVO> userVOList = BeanCopyUtil.copyListProperties(users, UserVO::new);

        assertThat(userVOList).hasSize(2);
        assertThat(userVOList.get(0).id).isEqualTo(1L);
        assertThat(userVOList.get(0).name).isEqualTo("anyujia");
        assertThat(userVOList.get(1).id).isEqualTo(2L);
        assertThat(userVOList.get(1).name).isEqualTo("gateway");
    }

    @Test
    void copyListPropertiesShouldReturnEmptyListWhenSourcesIsEmptyOrNull() {
        assertThat(BeanCopyUtil.copyListProperties(Collections.emptyList(), UserVO::new)).isEmpty();
        assertThat(BeanCopyUtil.copyListProperties(null, UserVO::new)).isEmpty();
    }

    @Test
    void copyListPropertiesShouldRejectNullTargetSupplier() {
        List<UserEntity> users = List.of(new UserEntity(1L, "anyujia"));

        assertThatThrownBy(() -> BeanCopyUtil.copyListProperties(users, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Target supplier must not be null");
    }

    public static class UserEntity {

        private Long id;
        private String name;

        public UserEntity() {
        }

        public UserEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class UserVO {

        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
