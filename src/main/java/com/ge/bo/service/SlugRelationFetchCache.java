package com.ge.bo.service;

import com.ge.bo.repository.SlugRelationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SlugRelationFetchCache {

  private final SlugRelationRepository slugRelationRepository;

  private volatile Set<String> fetchMasterSlugs = Set.of();

  @PostConstruct
  public void init() {
    reload();
  }

  public void reload() {
    this.fetchMasterSlugs = Set.copyOf(slugRelationRepository.findDistinctMasterSlugByRelationDirFetch());
  }

  public boolean hasFetch(String slug) {
    return fetchMasterSlugs.contains(slug);
  }
}
