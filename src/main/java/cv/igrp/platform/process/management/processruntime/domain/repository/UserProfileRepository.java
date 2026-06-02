package cv.igrp.platform.process.management.processruntime.domain.repository;


import cv.igrp.platform.process.management.processruntime.domain.models.UserProfile;
import cv.igrp.platform.process.management.processruntime.domain.models.UserProfileFilter;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserProfileRepository {

  UserProfile save(UserProfile userProfile);

  PageableLista<UserProfile> findAll(UserProfileFilter filter);

  Optional<UserProfile> findBySubjectOrEmail(String sub, String email);

  List<UserProfile> findBySubjectOrEmails(Set<String> subs, Set<String> emails);

}
