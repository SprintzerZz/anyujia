package com.anyujia.anyujiachatservice.contract;

import com.anyujia.anyujiachatapi.domain.dto.ConversationCreateReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationListReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.ChatUserVO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationVO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishConsultVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatJsonContractTest {

    private static final long LARGE_ID = 900000000000000001L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesIdsAsStringsButKeepsTimestampsNumeric() {
        ChatUserVO otherUser = new ChatUserVO();
        otherUser.setUserId(LARGE_ID);

        ConversationVO conversation = new ConversationVO();
        conversation.setSessionId(LARGE_ID);
        conversation.setHouseId(LARGE_ID);
        conversation.setHousePublishId(LARGE_ID);
        conversation.setLastMessageId(LARGE_ID);
        conversation.setLastMessageSeq("17");
        conversation.setLastSessionTime(LARGE_ID);
        conversation.setOtherUser(otherUser);

        JsonNode json = objectMapper.valueToTree(conversation);

        assertThat(json.get("sessionId").isTextual()).isTrue();
        assertThat(json.get("sessionId").asText()).isEqualTo("900000000000000001");
        assertThat(json.get("houseId").isTextual()).isTrue();
        assertThat(json.get("housePublishId").isTextual()).isTrue();
        assertThat(json.get("lastMessageId").isTextual()).isTrue();
        assertThat(json.get("lastMessageSeq").asText()).isEqualTo("17");
        assertThat(json.at("/otherUser/userId").isTextual()).isTrue();
        assertThat(json.get("lastSessionTime").isNumber()).isTrue();
    }

    @Test
    void serializesMessageAndHouseServiceIdsAsStrings() {
        MessageVO message = new MessageVO();
        message.setConversationSeq("18");
        message.setSessionId(LARGE_ID);
        message.setFromId(LARGE_ID);
        message.setCreateTime(LARGE_ID);

        HousePublishConsultVO house = new HousePublishConsultVO();
        house.setHousePublishId(LARGE_ID);
        house.setHouseId(LARGE_ID);
        house.setLandlordAppUserId(LARGE_ID);

        JsonNode messageJson = objectMapper.valueToTree(message);
        JsonNode houseJson = objectMapper.valueToTree(house);

        assertThat(messageJson.get("sessionId").isTextual()).isTrue();
        assertThat(messageJson.get("conversationSeq").asText()).isEqualTo("18");
        assertThat(messageJson.get("fromId").isTextual()).isTrue();
        assertThat(messageJson.get("createTime").isNumber()).isTrue();
        assertThat(houseJson.get("housePublishId").asText()).isEqualTo("900000000000000001");
        assertThat(houseJson.get("houseId").isTextual()).isTrue();
        assertThat(houseJson.get("landlordAppUserId").isTextual()).isTrue();
    }

    @Test
    void acceptsLargeRequestIdsEncodedAsStrings() throws Exception {
        ConversationCreateReqDTO request = objectMapper.readValue(
                "{\"counterpartyUserId\":\"900000000000000001\","
                        + "\"housePublishId\":\"900000000000000001\"}",
                ConversationCreateReqDTO.class);

        assertThat(request.getCounterpartyUserId()).isEqualTo(LARGE_ID);
        assertThat(request.getHousePublishId()).isEqualTo(LARGE_ID);
    }

    @Test
    void acceptsConversationListCursorWithStringSessionId() throws Exception {
        ConversationListReqDTO request = objectMapper.readValue(
                "{\"count\":20,\"lastSessionTime\":1700000000000,"
                        + "\"lastSessionId\":\"900000000000000001\"}",
                ConversationListReqDTO.class);

        assertThat(request.getLastSessionTime()).isEqualTo(1700000000000L);
        assertThat(request.getLastSessionId()).isEqualTo("900000000000000001");
    }
}
