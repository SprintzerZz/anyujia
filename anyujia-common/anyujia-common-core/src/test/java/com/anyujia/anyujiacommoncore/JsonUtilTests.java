package com.anyujia.anyujiacommoncore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.anyujia.anyujiacommoncore.utils.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

public class JsonUtilTests {

    @Test
    void obj2StringShouldSerializeObjectAndIgnoreNullField() {
        User user = new User(1, "anyujia", null);

        String json = JsonUtil.obj2String(user);

        assertThat(json).contains("\"id\":1");
        assertThat(json).contains("\"name\":\"anyujia\"");
        assertThat(json).doesNotContain("nickname");
    }

    @Test
    void obj2StringPrettyShouldSerializeFormattedJson() {
        User user = new User(1, "anyujia", null);

        String json = JsonUtil.obj2StringPretty(user);

        assertThat(json).contains(System.lineSeparator());
        assertThat(json).contains("\"id\" : 1");
    }

    @Test
    void string2ObjShouldDeserializeObjectAndIgnoreUnknownField() {
        String json = "{\"id\":1,\"name\":\"anyujia\",\"unknown\":\"ignored\"}";

        User user = JsonUtil.string2Obj(json, User.class);

        assertThat(user).isNotNull();
        assertThat(user.id).isEqualTo(1);
        assertThat(user.name).isEqualTo("anyujia");
    }

    @Test
    void string2ObjShouldSupportTypeReference() {
        String json = "{\"first\":{\"id\":1,\"name\":\"gateway\"}}";

        Map<String, User> users = JsonUtil.string2Obj(json, new TypeReference<Map<String, User>>() {
        });

        assertThat(users).containsKey("first");
        assertThat(users.get("first").name).isEqualTo("gateway");
    }

    @Test
    void string2ListShouldDeserializeJsonArray() {
        String json = "[{\"id\":1,\"name\":\"gateway\"},{\"id\":2,\"name\":\"mstemplate\"}]";

        List<User> users = JsonUtil.string2List(json, User.class);

        assertThat(users).hasSize(2);
        assertThat(users.get(0).name).isEqualTo("gateway");
        assertThat(users.get(1).id).isEqualTo(2);
    }

    @Test
    void string2MapShouldDeserializeJsonObject() {
        String json = "{\"gateway\":{\"id\":1,\"name\":\"gateway\"}}";

        Map<String, User> users = JsonUtil.string2Map(json, User.class);

        assertThat(users).containsKey("gateway");
        assertThat(users.get("gateway").id).isEqualTo(1);
    }

    @Test
    void shouldReturnNullWhenInputIsInvalid() {
        assertThat(JsonUtil.obj2String(null)).isNull();
        assertThat(JsonUtil.string2Obj("", User.class)).isNull();
        assertThat(JsonUtil.string2Obj("{", User.class)).isNull();
        assertThat(JsonUtil.string2List("", User.class)).isNull();
        assertThat(JsonUtil.string2Map("", User.class)).isNull();
    }

    public static class User {

        public Integer id;
        public String name;
        public String nickname;

        public User() {
        }

        public User(Integer id, String name, String nickname) {
            this.id = id;
            this.name = name;
            this.nickname = nickname;
        }
    }
}
