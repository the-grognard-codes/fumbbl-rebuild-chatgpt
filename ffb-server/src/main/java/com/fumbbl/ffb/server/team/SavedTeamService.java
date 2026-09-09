package com.fumbbl.ffb.server.team;

import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;
import com.fumbbl.ffb.server.team.bb2025.TeamValidation;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Current-catalog-only writes; immutable historical reads; no implicit migration or last-write-wins. */
public final class SavedTeamService {
	private final SavedTeamRepository repository;
	private final TeamValidation validation;
	private final SavedTeamJson json;
	private final String selectableCatalogVersion;
	public SavedTeamService(SavedTeamRepository repository, RosterCatalog catalog) { this(repository, catalog, RosterCatalog.VERSION); }
	public SavedTeamService(SavedTeamRepository repository, RosterCatalog catalog, String selectableCatalogVersion) {
		this.repository = repository; this.validation = new TeamValidation(catalog); this.json = new SavedTeamJson(catalog);
		this.selectableCatalogVersion = selectableCatalogVersion;
	}
	public List<SavedTeamRepository.Record> list(String owner) throws SQLException {
		List<SavedTeamRepository.Record> records = repository.list(owner);
		// List is also a read path: verify stored envelopes and evaluate every draft before projecting metadata.
		for (SavedTeamRepository.Record record : records) inspect(record);
		return records;
	}
	public Loaded load(String owner, String id) throws SQLException { return inspect(required(owner, id)); }
	public Loaded create(String owner, TeamDraft draft) throws SQLException {
		TeamValidation.Evaluation result = accepted(draft);
		SavedTeamDocument document = new SavedTeamDocument(UUID.randomUUID().toString(), owner, 1, draft, json.evaluation(result).toString());
		repository.insert(record(document));
		return new Loaded(document, "CURRENT", result);
	}
	public Loaded update(String owner, String id, int expectedVersion, TeamDraft draft) throws SQLException {
		checkVersion(expectedVersion);
		Loaded previous = load(owner, id);
		if (previous.document.documentVersion != expectedVersion) throw new Failure("CONFLICT");
		if (!"CURRENT".equals(previous.versionStatus)) throw new Failure(previous.versionStatus);
		if (!previous.document.draft.catalogVersion.equals(draft.catalogVersion)) throw new Failure("MIGRATION_REQUIRED");
		TeamValidation.Evaluation result = accepted(draft);
		if (expectedVersion >= 2147483646) throw new Failure("INVALID_DOCUMENT_VERSION");
		SavedTeamDocument document = new SavedTeamDocument(id, owner, expectedVersion + 1, draft, json.evaluation(result).toString());
		if (!repository.replace(record(document), expectedVersion)) throw new Failure("CONFLICT");
		return new Loaded(document, "CURRENT", result);
	}
	public Loaded importDocument(String owner, String text) throws SQLException {
		SavedTeamDocument document = json.decode(text);
		// Always evaluate untrusted imported choices, never reuse their validation claim.
		accepted(document.draft);
		SavedTeamRepository.Record existing = repository.find(owner, document.teamId);
		if (existing != null) {
			if (!owner.equals(document.owner)) throw new Failure("CONFLICT");
			return update(owner, document.teamId, document.documentVersion, document.draft);
		}
		if (document.documentVersion != 1) throw new Failure("CONFLICT");
		return create(owner, document.draft);
	}
	private TeamValidation.Evaluation accepted(TeamDraft draft) {
		TeamValidation.Evaluation result = validation.evaluate(draft);
		String status = versionStatus(draft);
		if (!"CURRENT".equals(status)) throw new Failure(status, result);
		if (!result.isValid()) throw new Failure("VALIDATION_FAILED", result);
		return result;
	}
	private String versionStatus(TeamDraft draft) {
		if (!RosterCatalog.VERSION.equals(draft.catalogVersion)) return "VERSION_UNAVAILABLE";
		return selectableCatalogVersion.equals(draft.catalogVersion) ? "CURRENT" : "MIGRATION_REQUIRED";
	}
	private Loaded inspect(SavedTeamRepository.Record record) {
		SavedTeamDocument document = json.decode(record.json);
		if (!document.teamId.equals(record.teamId) || !document.owner.equals(record.owner)
			|| document.documentVersion != record.documentVersion || !document.draft.catalogVersion.equals(record.catalogVersion)) {
			throw new Failure("PERSISTENCE_FAILED");
		}
		return new Loaded(document, versionStatus(document.draft), validation.evaluate(document.draft));
	}
	private SavedTeamRepository.Record required(String owner, String id) throws SQLException {
		SavedTeamRepository.Record record = repository.find(owner, id);
		if (record == null) throw new Failure("NOT_FOUND");
		return record;
	}
	private SavedTeamRepository.Record record(SavedTeamDocument document) {
		return new SavedTeamRepository.Record(document.teamId, document.owner, document.documentVersion,
			document.draft.catalogVersion, json.encode(document).toString());
	}
	private void checkVersion(int version) { if (version < 1 || version > 2147483646) throw new Failure("INVALID_DOCUMENT_VERSION"); }
	public static final class Loaded {
		public final SavedTeamDocument document;
		public final String versionStatus;
		public final TeamValidation.Evaluation validation;
		private Loaded(SavedTeamDocument document, String versionStatus, TeamValidation.Evaluation validation) {
			this.document = document; this.versionStatus = versionStatus; this.validation = validation;
		}
	}
	public static final class Failure extends IllegalArgumentException {
		public final String code;
		public final TeamValidation.Evaluation validation;
		public Failure(String code) { this(code, null); }
		public Failure(String code, TeamValidation.Evaluation validation) { super(code); this.code = code; this.validation = validation; }
	}
}
