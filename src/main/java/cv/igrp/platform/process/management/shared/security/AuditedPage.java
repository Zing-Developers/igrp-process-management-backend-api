package cv.igrp.platform.process.management.shared.security;

import java.util.List;

/** A page DTO whose {@code content} rows are audited — lets the response advice reach them. */
public interface AuditedPage {

  List<? extends AuditedResponse> getContent();

}
