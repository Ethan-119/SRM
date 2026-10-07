package com.srm.modules.cert.controller;

import com.srm.common.Result;
import com.srm.modules.cert.service.CertRenewalWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "供应商资质续期")
@RestController
@RequestMapping("/api/cert")
@RequiredArgsConstructor
public class CertRenewalController {

    private final CertRenewalWorkflowService certService;

    @Operation(summary = "供应商上传新资质，触发自动审核")
    @PostMapping("/review")
    public Result<Void> review(@RequestParam Long supplierId,
                               @RequestParam String certType,
                               @RequestParam String certNo,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newExpireDate) {
        certService.autoReviewCert(supplierId, certType, certNo, newExpireDate);
        return Result.ok();
    }
}
