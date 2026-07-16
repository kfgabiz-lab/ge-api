package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Connect Portal(CTP) 전송 페이로드 — IF_SRR_NAHP_CTP_0001 Target(S2) 필드 매핑
 */
public record CtpContactUsPayload(

        @JsonProperty("Type") String type,

        @JsonProperty("ProductInformationInquiryType") String productInformationInquiryType,

        @JsonProperty("Subject") String subject,

        @JsonProperty("ProductCategory") String productCategory,

        @JsonProperty("Email") String email,

        @JsonProperty("RequesterName") String requesterName,

        @JsonProperty("CompanyName") String companyName,

        @JsonProperty("Country") String country,

        @JsonProperty("Description") String description,

        @JsonProperty("Password") String password,

        @JsonProperty("MarketingOptInFlag") Boolean marketingOptInFlag,

        @JsonProperty("InquiryDate") String inquiryDate,

        @JsonProperty("InquiryProcessingExceptionFlag") Boolean inquiryProcessingExceptionFlag,

        @JsonProperty("InquiryProcessingExceptionEmail") String inquiryProcessingExceptionEmail) {
}