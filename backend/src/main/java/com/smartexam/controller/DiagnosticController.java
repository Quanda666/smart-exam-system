package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网络诊断工具（仅用于排查问题，生产环境应禁用）
 */
@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {

    @GetMapping("/smtp-test")
    public ApiResponse<Map<String, Object>> testSmtpConnection(
            @RequestParam(defaultValue = "smtp.qq.com") String host,
            @RequestParam(defaultValue = "587") int port) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", host);
        result.put("port", port);
        result.put("timestamp", System.currentTimeMillis());

        long startTime = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 10000);
            long latency = System.currentTimeMillis() - startTime;
            result.put("status", "SUCCESS");
            result.put("latencyMs", latency);
            result.put("message", "连接成功");
            return ApiResponse.ok(result);
        } catch (SocketTimeoutException e) {
            long latency = System.currentTimeMillis() - startTime;
            result.put("status", "TIMEOUT");
            result.put("latencyMs", latency);
            result.put("error", "连接超时：" + e.getMessage());
            return ApiResponse.ok("连接超时", result);
        } catch (IOException e) {
            long latency = System.currentTimeMillis() - startTime;
            result.put("status", "FAILED");
            result.put("latencyMs", latency);
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return ApiResponse.ok("连接失败", result);
        }
    }
}
