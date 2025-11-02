package com.ron.ronaiagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health Check Controller - 健康检查控制器
 *
 * @author admin
 * @date 2025/11/2 下午10:20
 */
@RestController
@RequestMapping("/health")
@Tag(name = "健康检查", description = "系统健康状态检查相关接口")
public class HealthController {

    @GetMapping
    @Operation(
            summary = "健康检查",
            description = "检查应用程序是否正常运行"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "服务运行正常",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(description = "服务状态", example = "OK")
                    )
            )
    })
    public String healthCheck() {
        return "OK";
    }
}
