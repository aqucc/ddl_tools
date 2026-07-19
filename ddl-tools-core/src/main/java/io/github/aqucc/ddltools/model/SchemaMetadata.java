package io.github.aqucc.ddltools.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 1スキーマ分のメタ情報。オブジェクト種別ごとのリストを保持する。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SchemaMetadata {

    private String name;
    private List<TableMetadata> tables = new ArrayList<TableMetadata>();
    private List<ViewMetadata> views = new ArrayList<ViewMetadata>();
    private List<MaterializedViewMetadata> materializedViews = new ArrayList<MaterializedViewMetadata>();
    private List<TriggerMetadata> triggers = new ArrayList<TriggerMetadata>();
    private List<RoutineMetadata> routines = new ArrayList<RoutineMetadata>();
    private List<SequenceMetadata> sequences = new ArrayList<SequenceMetadata>();
    private List<SynonymMetadata> synonyms = new ArrayList<SynonymMetadata>();
    private List<PackageMetadata> packages = new ArrayList<PackageMetadata>();
    private List<GenericObjectMetadata> others = new ArrayList<GenericObjectMetadata>();

    public SchemaMetadata() {
    }

    public SchemaMetadata(String name) {
        this.name = name;
    }

    /** テーブル名で検索(存在しなければnull)。大文字小文字は区別しない。 */
    public TableMetadata findTable(String tableName) {
        for (TableMetadata t : tables) {
            if (t.getName().equalsIgnoreCase(tableName)) {
                return t;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TableMetadata> getTables() {
        return tables;
    }

    public void setTables(List<TableMetadata> tables) {
        this.tables = tables;
    }

    public List<ViewMetadata> getViews() {
        return views;
    }

    public void setViews(List<ViewMetadata> views) {
        this.views = views;
    }

    public List<MaterializedViewMetadata> getMaterializedViews() {
        return materializedViews;
    }

    public void setMaterializedViews(List<MaterializedViewMetadata> materializedViews) {
        this.materializedViews = materializedViews;
    }

    public List<TriggerMetadata> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<TriggerMetadata> triggers) {
        this.triggers = triggers;
    }

    public List<RoutineMetadata> getRoutines() {
        return routines;
    }

    public void setRoutines(List<RoutineMetadata> routines) {
        this.routines = routines;
    }

    public List<SequenceMetadata> getSequences() {
        return sequences;
    }

    public void setSequences(List<SequenceMetadata> sequences) {
        this.sequences = sequences;
    }

    public List<SynonymMetadata> getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(List<SynonymMetadata> synonyms) {
        this.synonyms = synonyms;
    }

    public List<PackageMetadata> getPackages() {
        return packages;
    }

    public void setPackages(List<PackageMetadata> packages) {
        this.packages = packages;
    }

    public List<GenericObjectMetadata> getOthers() {
        return others;
    }

    public void setOthers(List<GenericObjectMetadata> others) {
        this.others = others;
    }
}
