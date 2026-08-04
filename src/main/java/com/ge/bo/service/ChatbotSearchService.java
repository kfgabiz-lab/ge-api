package com.ge.bo.service;

import com.ge.bo.dto.ChatbotSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.LocalTime;

/**
 * 외부 AI 챗봇 API 호출 Service.
 * 외부 API 응답 형식
 * event: response.chunk
 * data: {...}
 *
 * event: response.chunk
 * data: {...}
 *
 * event: response.complete
 * data: {...}
 */
@Slf4j
@Service
public class ChatbotSearchService {

    /**
     * 챗봇 답변 일부가 전달되는 이벤트명.
     */
    private static final String EVENT_CHUNK =
            "response.chunk";

    /**
     * 챗봇 답변 생성이 완료되었을 때 전달되는 이벤트명.
     */
    private static final String EVENT_COMPLETED =
            "response.completed";

    private static final String EVENT_KEYWORD = "response.keyword";

    /**
     * ServerSentEvent<String>의 제네릭 타입 정보를
     * 런타임까지 유지하기 위한 타입 정의.
     */
    private static final ParameterizedTypeReference<
            ServerSentEvent<String>
            > SSE_STRING_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final String KEYWORD_1 = "USPE Series";
    private static final String[] RELATED_KEYWORDS_1 = {
            "USPE", "SPD", "Surge Protective Device UL SPD", "surge protection solution", "surge protective device"
    };

    private static final String KEYWORD_2 = "MCCB";
    private static final String[] RELATED_KEYWORDS_2 = {
            "배선용차단기", "ELCB", "Molded Case Circuit Breaker", "누전차단기", "UL MCCB"
    };

    private static final String KEYWORD_3 = "SPD";
    private static final String[] RELATED_KEYWORDS_3 = {
            "surge protective device", "UL SPD", "renewable energy solution", "SPD Disconnector", "DC MCCB"
    };

    private static final String KEYWORD_4 = "VFD";
    private static final String[] RELATED_KEYWORDS_4 = {
            "Inverter", "AC Variable Speed Drive", "AC Drive", "AC 가변속 드라이브", "인버터"
    };

    private static final String RESPONSE_1 =
            "# **USPE Series SPD**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- **Surge capacity per phase:** **25 ~ 50 kA**\n" +
            "\n" +
            "- **Nominal discharge current (In):** **10 kA**\n" +
            "\n" +
            "Please refer to the official catalog for precise specifications and technical requirements.";

    private static final String RESPONSE_2 =
            "# MCCB\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "## Overview\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- **MCCB** appears in the provided materials as **Molded Case Circuit Breaker**.\n" +
            "\n" +
            "- In the **Susol Smart MCCB** material, it is described as a product developed by combining **digital technology** with LS ELECTRIC’s **power device technology accumulated over 40 years**.\n" +
            "\n" +
            "- The document states that the **relay and measurement functions for line protection** have been upgraded.\n" +
            "\n" +
            "- It also states that, by using **accessory devices for connectivity between low-voltage devices**, it is possible to **diagnose and maintain devices by collecting and analyzing data**.\n" +
            "\n" +
            "\n" +
            "\n" +
            "## Basic information\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "### Susol Smart MCCB\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- Supports **on-site monitoring** and **on-site maintenance convenience** through a **short-range wireless mobile app service**.\n" +
            "\n" +
            "- Mobile app services include:\n" +
            "  - **Real-time system and device operation status monitoring**\n" +
            "  - **Energy use and failure analysis service measure**\n" +
            "\n" +
            "- Mobile application features include:\n" +
            "  - **Device search and automatic recognition**\n" +
            "  - **Device status and operation information inquiry**\n" +
            "  - **Graphic chart by element**\n" +
            "\n" +
            "\n" +
            "\n" +
            "### GridSol CARE related configuration\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- **Smart MCCB** is listed as a component of **GridSol CARE**, along with:\n" +
            "  - Upper level system\n" +
            "  - Communication device\n" +
            "  - Accessory device\n" +
            "  - ACB\n" +
            "  - MCB\n" +
            "\n" +
            "- The document states that GridSol CARE provides **power monitoring and control functions remotely** through its software.\n" +
            "\n" +
            "\n" +
            "\n" +
            "## Features found in the provided documents\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "### UL489 MCCB related features\n" +
            "\n" +
            "\n" +
            "\n" +
            "From the **UL891 switchboard solution** material using **UL489 MCCBs**:\n" +
            "\n" +
            "\n" +
            "- Meets **UL67 / UL891 certification standards** for bus straps and interiors utilizing UL489 MCCBs\n" +
            "\n" +
            "- Provides flexibility through:\n" +
            "  - **Five types of interiors**\n" +
            "  - **Three types of bus straps**\n" +
            "  - **A wide range of MCCBs**\n" +
            "\n" +
            "- Described as:\n" +
            "  - **Cost effective**\n" +
            "  - Allowing **safe installation**\n" +
            "  - Allowing **interchangeability**\n" +
            "\n" +
            "\n" +
            "\n" +
            "### Listed panel-related features\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- **UL67 / UL891 Panelboards**\n" +
            "\n" +
            "- **UL489 Molded case circuit breakers**\n" +
            "\n" +
            "- **Main bus, 1200 / 2000 / 2400 / 4000 / 6000A copper**\n" +
            "\n" +
            "- **Branch-bus direct connection**\n" +
            "\n" +
            "- **Up to 1200A breaker mounted as a branch device**\n" +
            "\n" +
            "- **Double branched 150, 250 and 400AF breakers**\n" +
            "\n" +
            "- **Interior maximum short circuit interrupting rating 100kA at 480Vac**\n" +
            "\n" +
            "- **Individual breaker protection cover plates**\n" +
            "\n" +
            "\n" +
            "\n" +
            "## Product line examples shown in the documents\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "### UL489 MCCB supply scope\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- **UTS1200**\n" +
            "\n" +
            "- **UTS800**\n" +
            "\n" +
            "- **UTS600**\n" +
            "\n" +
            "- **UTS400**\n" +
            "\n" +
            "- **UTS250**\n" +
            "\n" +
            "- **UTS150**\n" +
            "\n" +
            "\n" +
            "\n" +
            "### Susol MCCB overview chart labels\n" +
            "\n" +
            "\n" +
            "\n" +
            "The overview image shows the following model labels:\n" +
            "\n" +
            "\n" +
            "- **TD100**\n" +
            "\n" +
            "- **TD160**\n" +
            "\n" +
            "- **TS100**\n" +
            "\n" +
            "- **TS160**\n" +
            "\n" +
            "- **TS250**\n" +
            "\n" +
            "- **TS400**\n" +
            "\n" +
            "- **TS630**\n" +
            "\n" +
            "- **TS800**\n" +
            "\n" +
            "- **TS1000**\n" +
            "\n" +
            "- **TS1250**\n" +
            "\n" +
            "- **TS1600**\n" +
            "\n" +
            "\n" +
            "\n" +
            "## Additional information\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- One document states that **LSIS MCCB can operate ON / OFF remotely when using MOP (Motor Operator)**.\n" +
            "\n" +
            "- It describes **MOP** as **an accessory which contains a motor for operating**.\n" +
            "\n" +
            "- The applicable range shown for **Susol MCCB** is:\n" +
            "  - **TD160 ~ TS800**\n" +
            "\n" +
            "\n" +
            "\n" +
            "## Note\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- Please refer to the official catalog for precise specifications and technical requirements.\n" +
            "\n" +
            "- For manuals, certificates, CAD drawings, or other detailed resources, please visit the [LS ELECTRIC Download Center](https://www.ls-electric.com/support/download-center).";

    private static final String RESPONSE_3 =
            "# **SPD**\n" +
            "\n" +
            "\n" +
            "\n" +
            "**SPD** stands for **Surge Protective Device**.\n" +
            "\n" +
            "\n" +
            "\n" +
            "## **Overview**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- SPD is installed in the **power input terminal of precision control equipment** to **minimize damage to the load**.\n" +
            "\n" +
            "- According to the document, surge is a **transient waveform of electric current, voltage, or power** with **rapidly increasing and gradually decreasing characteristics**.\n" +
            "\n" +
            "- The major source of surge occurrence described in the document is **lightning**.\n" +
            "\n" +
            "- For direct-strike-related protection, the document states that the **proper protection region should be protected first with a selected lightning rod**, and **SPD should be used to prevent facility damage in the system**.\n" +
            "\n" +
            "\n" +
            "\n" +
            "## **Basic operating concept**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- When a surge enters the system, SPD provides a **low-impedance path** so that the **surge current flows through the SPD instead of the load**.\n" +
            "\n" +
            "- The document explains that **MOV** is normally in a **very high impedance state**.\n" +
            "\n" +
            "- When a voltage surge occurs, the **impedance of MOV is greatly reduced**, creating a **low-impedance path** for surge current.\n" +
            "\n" +
            "- As a result, SPD helps prevent the voltage from rising sharply.\n" +
            "\n" +
            "\n" +
            "\n" +
            "## **Types of SPD**\n" +
            "\n" +
            "\n" +
            "\n" +
            "The document classifies SPDs into two types depending on their features:\n" +
            "\n" +
            "\n" +
            "- **Voltage switching type**\n" +
            "\n" +
            "- **Voltage restricting type**\n" +
            "\n" +
            "\n" +
            "\n" +
            "### **Voltage switching type SPD**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- If a surge exceeding the **discharge start voltage** enters, **discharge occurs momentarily for 1 cycle to 2 cycles**.\n" +
            "\n" +
            "- During discharge, it becomes a **momentary short circuit**, so **rapid current flows through SPD** with a momentary voltage drop.\n" +
            "\n" +
            "- It remains **open below the discharge start voltage**.\n" +
            "\n" +
            "- It automatically returns to the **open state** when the surge is removed.\n" +
            "\n" +
            "- The document lists **gas tube elements** and **air gap elements** as discharge-type elements.\n" +
            "\n" +
            "\n" +
            "\n" +
            "### **Voltage restricting type SPD**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- This type **limits voltage only to a specific level**.\n" +
            "\n" +
            "- The limiting voltage is called **clamping voltage** or **suppression voltage**.\n" +
            "\n" +
            "- The restricting voltage is determined by the correlation between the **line impedance** and the **lightning rod impedance**.\n" +
            "\n" +
            "- The document lists these elements for this type:\n" +
            "  - **MOV (Metal Oxide Varistor)**\n" +
            "  - **Semiconductor diodes**\n" +
            "  - **Sidactors**\n" +
            "\n" +
            "\n" +
            "\n" +
            "## **Key element characteristics**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "### **MOV**\n" +
            "\n" +
            "\n" +
            "\n" +
            "The document states that:\n" +
            "\n" +
            "\n" +
            "- **MOV is the most reliable technology** among technologies attenuating surge voltage.\n" +
            "\n" +
            "- **96% or more SPD for power** selects **MOV**.\n" +
            "\n" +
            "- MOV is designed so that:\n" +
            "  - **Current rarely flows at normal voltage**\n" +
            "  - **Current flows a lot at high voltage**\n" +
            "  - **The voltage drop does not rise sharply even when a lot of current flows**\n" +
            "\n" +
            "\n" +
            "\n" +
            "### **SAD**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- **SAD (Silicon Avalanche Diode)** is often used as the SPD for the **data line or communication line**.\n" +
            "\n" +
            "\n" +
            "\n" +
            "## **Application example**\n" +
            "\n" +
            "\n" +
            "\n" +
            "The document shows SPD application based on facility configuration, including:\n" +
            "\n" +
            "\n" +
            "- **Incoming panel**\n" +
            "\n" +
            "- **Distribution panel**\n" +
            "\n" +
            "- **Distribution board**\n" +
            "\n" +
            "- **UPS**\n" +
            "\n" +
            "- **Server**\n" +
            "\n" +
            "- **Network**\n" +
            "\n" +
            "- **Communication antenna**\n" +
            "\n" +
            "- **MCC**\n" +
            "\n" +
            "- **Cooler**\n" +
            "\n" +
            "- **Cooling tower**\n" +
            "\n" +
            "- **IS Room**\n" +
            "\n" +
            "It also states:\n" +
            "\n" +
            "\n" +
            "- **BKS-A/C/E products are used for power**\n" +
            "\n" +
            "- The legend indicates:\n" +
            "  - **Yellow: For power**\n" +
            "  - **Green: For data and communication**\n" +
            "\n" +
            "\n" +
            "\n" +
            "## **SPD Disconnector information**\n" +
            "\n" +
            "\n" +
            "\n" +
            "The document also includes **SPD Disconnector** information.\n" +
            "\n" +
            "\n" +
            "- The **SPD Disconnector** is responsible for **disconnecting the SPD from the system** in the event of:\n" +
            "  - **Failure**\n" +
            "  - **Maintenance of the SPD**\n" +
            "\n" +
            "\n" +
            "\n" +
            "### **Features of SPD Disconnector**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- **Convenient SPD replacement**\n" +
            "\n" +
            "- **High surge resistance**\n" +
            "\n" +
            "- **Leakage current detection**\n" +
            "\n" +
            "- **Prevent malfunctions in case of short circuit**\n" +
            "\n" +
            "- **Easy installation using DIN rail**\n" +
            "\n" +
            "\n" +
            "\n" +
            "## **UL SPD overview**\n" +
            "\n" +
            "\n" +
            "\n" +
            "For the **UL Surge Protective Device** lineup, the document states:\n" +
            "\n" +
            "\n" +
            "- Designed in compliance with **ANSI/UL 1449 5th Edition** and **CSA C22.2**\n" +
            "\n" +
            "- Intended for **North American applications**\n" +
            "\n" +
            "\n" +
            "\n" +
            "### **Series mentioned**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- **USP Series**\n" +
            "\n" +
            "- **USPV Series**\n" +
            "\n" +
            "- **USPE Series**\n" +
            "\n" +
            "- **USPB Series**\n" +
            "\n" +
            "- **USPC Series**\n" +
            "\n" +
            "\n" +
            "\n" +
            "### **Mounting / certification overview**\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "- **USP and USPV Series**\n" +
            "  - **DIN-rail mounted**\n" +
            "  - **UL Recognized Type 1CA**\n" +
            "  - Provide protection at the **service entrance**\n" +
            "\n" +
            "\n" +
            "- **USPE, USPB, and USPC Series**\n" +
            "  - **Panel-type SPDs**\n" +
            "  - **UL Listed Type 1 and Type 2**\n" +
            "  - Provide surge protection for **commercial and industrial systems**\n" +
            "\n" +
            "\n" +
            "\n" +
            "## **Reference image**\n" +
            "\n" +
            "\n" +
            "\n" +
            "![The conceptual diagram of the surge restriction of SPD](https://cxai.ls-electric.com/api/v1/chat/blob/inline/customer-knowledge-private/artifacts/operations/power/009569cc627e42d2a3a0e64039d3c78d/assets/1be7769c4845475689355107fd16169f_fig_064_page35.png?se=2026-08-04T03%3A13%3A20Z&sp=r&sv=2026-06-06&sr=b&sig=IicSb2XnWE5hFUAZktB7kRffX65VT5%2BXrd1zw32qUVw%3D)\n" +
            "\n" +
            "Please refer to the official catalog for precise specifications and technical requirements.\n" +
            "\n" +
            "For manuals, certificates, CAD drawings, or other detailed resources, please visit the [LS ELECTRIC Download Center](https://www.ls-electric.com/support/download-center).";

    private static final String RESPONSE_4 =
            "## VFD Product Overview and Basic Information\n" +
            "\n" +
            "### Overview from the retrieved LS ELECTRIC documents\n" +
            "\n" +
            "| Area | Confirmed information |\n" +
            "|------|--------|\n" +
            "| Product lineup | The low-voltage VFD catalog shows **iS7** as a **general-purpose** drive series and **H100** as a **fan & pump** drive series. The **S100** manual describes **LSLV-S100** as a **three-phase standard VFD**. |\n" +
            "| Basic operation | For **iS7** and **H100**, the command source is listed as **keypad, terminal block, or communication control**. Frequency setting is shown as **analog** (`-10~10V`, `0~10V`, `0~20mA`) and **digital keypad**; **H100** also lists **pulse train input**. |\n" +
            "| Related options | The catalog lists peripherals such as **AC reactor, harmonic filter, DC reactor, dv/dt filter, sine wave filter, EMI filter,** and **bypass line**. |\n" +
            "\n" +
            "### Application note\n" +
            "\n" +
            "- The S100 manual states that when **single-phase power** is applied to a **three-phase VFD**, the DC bus ripple becomes higher and input current/harmonics increase.\n" +
            "- Because of this, the drive power rating needs to be **reduced (derated)** for single-phase use.\n" +
            "\n" +
            "![Figure-1 Typical Three-Phase Configuration. 3-phase input, DCL, DC capacitor, DC link voltage 360 Hz Ripple, Phase voltage, Rectifier input current Approximately 40% I-THD. 검색 키워드: Figure-1, Typical Three-Phase Configuration, 3-phase input, DCL, DC capacitor, DC link voltage, 360 Hz Ripple, Phase voltage, Rectifier input current, Approximately 40% I-THD](https://cxai.ls-electric.com/api/v1/chat/blob/inline/customer-knowledge-private/artifacts/operations/automation/633505be2fbf485494556269cf9a75d5/assets/670a949eed364268805c0cae34976481_fig_184_page254.png?se=2026-08-04T03%3A14%3A19Z&sp=r&sv=2026-06-06&sr=b&sig=2/7mOfgZs%2BZ7SWKFIFyeiE9Lc3ZZcMJTfMn0JdCP7Ng%3D)\n" +
            "\n" +
            "Which VFD series or topic would you like to explore in more detail, such as model selection, wiring, communication, or protection functions?\n" +
            "\n" +
            "### 📖 References\n" +
            "- [Catalog for Low Voltage VFD.pdf (p.12-14)](https://cxai.ls-electric.com/api/v1/chat/blob/attachment/customer-knowledge-private/artifacts/operations/automation/ca5293baa2fe480db8d9a95482e15cb4/source/13b923d200d14805bcf7a5919cf74968_Catalog%20for%20Low%20Voltage%20VFD.pdf?se=2026-08-04T03%3A14%3A19Z&sp=r&sv=2026-06-06&sr=b&rscd=attachment%3B%20filename%3D%22Catalog%20for%20Low%20Voltage%20VFD.pdf%22%3B%20filename%2A%3DUTF-8%27%27Catalog%2520for%2520Low%2520Voltage%2520VFD.pdf&rsct=application/pdf&sig=ZnmtnESFyE/LJWGvkEiAZF0FB2Vb0wCVlnewfjjwAoY%3D)\n" +
            "- [S100_Simple_Manual_English_V4.2.pdf (p.254-255)](https://cxai.ls-electric.com/api/v1/chat/blob/attachment/customer-knowledge-private/artifacts/operations/automation/4033d6a2d318466782af7827efc389b3/source/6ac511dfae6d418bbf0a0075a3799fda_S100_Simple_Manual_English_V4.2.pdf?se=2026-08-04T03%3A14%3A19Z&sp=r&sv=2026-06-06&sr=b&rscd=attachment%3B%20filename%3D%22S100_Simple_Manual_English_V4.2.pdf%22%3B%20filename%2A%3DUTF-8%27%27S100_Simple_Manual_English_V4.2.pdf&rsct=application/pdf&sig=laOIfpbgYrKplJ1TTLwM0IZfmxmuBOVlLldJrGL8OkE%3D)";

    /**
     * 챗봇 SSE 호출 전용 WebClient.
     */
    private final WebClient chatbotWebClient;

    /**
     * 외부 챗봇 API 주소.
     */
    private final String chatbotApiUrl;

    /**
     * 외부 챗봇 API 인증키.
     */
    private final String chatbotApiKey;

    /**
     * 외부 챗봇 API 인증 헤더 이름.
     *
     * 예:
     * api-key
     * Authorization
     */
    private final String chatbotApiKeyHeader;

    public ChatbotSearchService(
            @Qualifier("chatbotWebClient")
            WebClient chatbotWebClient,

            @Value("${ls.lse.out-api.chatbot-search.api-url}")
            String chatbotApiUrl,

            @Value("${ls.lse.out-api.chatbot-search.api-key}")
            String chatbotApiKey
    ) {
        this.chatbotWebClient = chatbotWebClient;
        this.chatbotApiUrl = chatbotApiUrl;
        this.chatbotApiKey = "Bearer " + chatbotApiKey;
        this.chatbotApiKeyHeader = HttpHeaders.AUTHORIZATION;
    }

    /**
     * 외부 챗봇 API를 호출하고 SSE 이벤트를 실시간으로 반환한다.
     *
     * response.chunk 이벤트는 계속 전달하고,
     * response.complete 이벤트까지 전달한 뒤 스트림을 종료한다.
     *
     * @param request 챗봇 검색 요청
     * @return 외부 챗봇의 SSE 응답 스트림
     */
    public Flux<ServerSentEvent<String>> search(
            ChatbotSearchRequest request
    ) {

        return chatbotWebClient
                .post()
                .uri(chatbotApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header(
                        chatbotApiKeyHeader,
                        chatbotApiKey
                )
                .bodyValue(request)
                .exchangeToFlux(response -> {

                    log.info(
                            "[CHATBOT HTTP RESPONSE] status={}, contentType={}, timestamp={}",
                            response.statusCode(),
                            response.headers()
                                    .contentType()
                                    .orElse(null),
                            LocalTime.now()
                    );

                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(
                                SSE_STRING_TYPE
                        );
                    }

                    return response
                            .bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMapMany(body ->
                                    Flux.error(
                                            new IllegalStateException(
                                                    "챗봇 API 호출 실패. status="
                                                            + response.statusCode()
                                                            + ", body="
                                                            + body
                                            )
                                    )
                            );
                })
                .doOnNext(event ->
                        log.info(
                                "[CHATBOT SSE RECEIVED] event={}, id={}, data={}, timestamp={}",
                                event.event(),
                                event.id(),
                                event.data(),
                                LocalTime.now()
                        )
                )
                .filter(event -> {
                    String eventName = event.event();

                    return EVENT_KEYWORD.equals(eventName)
                            || EVENT_CHUNK.equals(eventName)
                            || EVENT_COMPLETED.equals(eventName);
                })
                .doOnNext(event ->
                        log.info(
                                "[CHATBOT SSE FORWARD] event={}, id={}, data={}, timestamp={}",
                                event.event(),
                                event.id(),
                                event.data(),
                                LocalTime.now()
                        )
                )
                .doOnComplete(() ->
                        log.info(
                                "[CHATBOT STREAM COMPLETE] url={}, timestamp={}",
                                chatbotApiUrl,
                                LocalTime.now()
                        )
                )
                .doOnCancel(() ->
                        log.info(
                                "[CHATBOT STREAM CANCELLED] url={}",
                                chatbotApiUrl
                        )
                )
                .doOnError(error ->
                        log.error(
                                "[CHATBOT STREAM ERROR] url={}, message={}",
                                chatbotApiUrl,
                                error.getMessage(),
                                error
                        )
                );
    }
}
