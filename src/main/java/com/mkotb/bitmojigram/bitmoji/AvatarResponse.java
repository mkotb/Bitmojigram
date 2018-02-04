package com.mkotb.bitmojigram.bitmoji;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AvatarResponse {
    private int avatarId;
    private int avatarVersion;
    private String id;
    private int gender; // where 1 = male
    @SerializedName("avatar_version_uuid")
    private UUID avatarVersionId;
}
