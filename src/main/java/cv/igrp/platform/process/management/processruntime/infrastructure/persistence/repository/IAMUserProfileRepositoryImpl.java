package cv.igrp.platform.process.management.processruntime.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.processruntime.domain.models.UserProfile;
import cv.igrp.platform.process.management.processruntime.domain.models.UserProfileFilter;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.processruntime.mappers.UserProfileMapper;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.IAMUserProfileEntityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class IAMUserProfileRepositoryImpl implements UserProfileRepository {

  private final IAMUserProfileEntityRepository repository;
  private final UserProfileMapper mapper;

  public IAMUserProfileRepositoryImpl(IAMUserProfileEntityRepository repository,
                                      UserProfileMapper mapper
  ) {
    this.repository = repository;
    this.mapper = mapper;
  }

  @Override
  public UserProfile save(UserProfile userProfile) {
    return mapper.toModel(
        repository.save(mapper.toEntity(userProfile))
    );
  }

  @Override
  public PageableLista<UserProfile> findAll(UserProfileFilter filter) {
    return null;
  }

  private Optional<UserProfile> findBySub(String sub) {
    if (!StringUtils.hasText(sub)) {
      return Optional.empty();
    }
    return repository.findBySub(sub).map(mapper::toModel);
  }

  @Override
  public Optional<UserProfile> findBySubjectOrEmail(String sub, String email) {
    Optional<UserProfile> profile = findBySub(sub);
    if (profile.isPresent() || !StringUtils.hasText(email)) {
      return profile;
    }
    return repository.findByEmail(email).map(mapper::toModel);
  }

  @Override
  public List<UserProfile> findBySubjectOrEmails(Set<String> subs, Set<String> emails) {
    Map<UUID, UserProfile> profiles = new LinkedHashMap<>();

    if (!CollectionUtils.isEmpty(subs)) {
      repository.findBySubIn(subs)
          .forEach(entity -> profiles.put(entity.getId(), mapper.toModel(entity)));
    }

    if (!CollectionUtils.isEmpty(emails)) {
      repository.findByEmailIn(emails)
          .forEach(entity -> profiles.putIfAbsent(entity.getId(), mapper.toModel(entity)));
    }

    return List.copyOf(profiles.values());
  }

}
