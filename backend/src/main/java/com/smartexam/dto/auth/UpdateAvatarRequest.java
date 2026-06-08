package com.smartexam.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateAvatarRequest {

    // 头像以 base64 dataURL 形式存储（前端已压缩为约 200x200）；
    // 上限 2,000,000 字符防止超大图打满数据库与传输，前端会先压缩。
    @NotBlank(message = "头像数据不能为空")
    @Size(max = 2_000_000, message = "头像数据过大，请压缩后再上传")
    private String avatar;

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}
