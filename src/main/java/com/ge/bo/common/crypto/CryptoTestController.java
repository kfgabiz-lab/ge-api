package com.ge.bo.common.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cryptoTest")
public class CryptoTestController {

    private final Aes256Utils aes256Utils;

    @GetMapping("/enc")
    public String set(@RequestParam String val) {

        String encrypted = aes256Utils.encrypt(val);

        log.info("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
        log.info("encrypted ::::::::: " + encrypted);
        log.info("val ::::::::: " + val);
        log.info("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");

        return encrypted == null ? "ERROR" : encrypted;
    }

    @GetMapping("/dec")
    public String get(@RequestParam String val) {

        String decrypted = aes256Utils.decrypt(val);
        log.info("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
        log.info("val ::::::::: " + val);
        log.info("decrypted ::::::::: " + decrypted);
        log.info("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");

        return decrypted == null ? "ERROR" : decrypted;
    }
}