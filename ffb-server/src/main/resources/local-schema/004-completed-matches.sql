ALTER TABLE ffb_prepared_matches
  DROP CONSTRAINT `${sizeConstraint}`,
  MODIFY document_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  ADD CONSTRAINT completed_match_size CHECK (OCTET_LENGTH(document_json) <= 16842752)
