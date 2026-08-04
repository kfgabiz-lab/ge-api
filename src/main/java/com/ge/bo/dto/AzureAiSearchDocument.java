package com.ge.bo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AzureAiSearchDocument (
        @JsonProperty("@search.score")
        Double searchScore,

        @JsonProperty("@search.highlights")
        AzureAiSearchHighlights searchHighlights,

        String id,

        @JsonProperty("file_name")
        String fileName,

        @JsonProperty("file_path")
        String filePath,

        @JsonProperty("file_size")
        Long fileSize,

        @JsonProperty("last_modified")
        String lastModified,

        List<String> category,

        @JsonProperty("chunk_index")
        Integer chunkIndex,

        @JsonProperty("total_chunks")
        Integer totalChunks,

        @JsonProperty("file_name_search")
        String fileNameSearch
){
}
