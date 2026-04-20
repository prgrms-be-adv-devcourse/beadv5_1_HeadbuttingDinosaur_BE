package org.example.ai.infrastructure.external.dto.req;

public record ActionLogRequest(
    String userId,
    String actionTypes
) {

    public static ActionLogRequest ofRecent(String userId){
        return new ActionLogRequest(userId, "VIEW,DETAIL_VIEW,DWELL_TIME");
    }

}
