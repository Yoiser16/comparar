package com.link.comparar.model;

import java.util.List;
import java.util.Map;

public class ComparisonResult {
    private List<String> matchingIds;
    private List<String> onlyInCsv;
    private List<String> onlyInExcel;
    private int totalMatches;
    private int totalCsvIds;
    private int totalExcelIds;

    // Nuevos campos para datos completos
    private List<FileRecord> matchingRecords;
    private Map<String, FileRecord> csvRecordsMap;
    private Map<String, FileRecord> excelRecordsMap;

    public ComparisonResult() {
    }

    public ComparisonResult(List<String> matchingIds, List<String> onlyInCsv, List<String> onlyInExcel) {
        this.matchingIds = matchingIds;
        this.onlyInCsv = onlyInCsv;
        this.onlyInExcel = onlyInExcel;
        this.totalMatches = matchingIds.size();
        this.totalCsvIds = matchingIds.size() + onlyInCsv.size();
        this.totalExcelIds = matchingIds.size() + onlyInExcel.size();
    }

    // Getters and Setters
    public List<String> getMatchingIds() {
        return matchingIds;
    }

    public void setMatchingIds(List<String> matchingIds) {
        this.matchingIds = matchingIds;
        this.totalMatches = matchingIds != null ? matchingIds.size() : 0;
    }

    public List<String> getOnlyInCsv() {
        return onlyInCsv;
    }

    public void setOnlyInCsv(List<String> onlyInCsv) {
        this.onlyInCsv = onlyInCsv;
    }

    public List<String> getOnlyInExcel() {
        return onlyInExcel;
    }

    public void setOnlyInExcel(List<String> onlyInExcel) {
        this.onlyInExcel = onlyInExcel;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    public void setTotalMatches(int totalMatches) {
        this.totalMatches = totalMatches;
    }

    public int getTotalCsvIds() {
        return totalCsvIds;
    }

    public void setTotalCsvIds(int totalCsvIds) {
        this.totalCsvIds = totalCsvIds;
    }

    public int getTotalExcelIds() {
        return totalExcelIds;
    }

    public void setTotalExcelIds(int totalExcelIds) {
        this.totalExcelIds = totalExcelIds;
    }

    public List<FileRecord> getMatchingRecords() {
        return matchingRecords;
    }

    public void setMatchingRecords(List<FileRecord> matchingRecords) {
        this.matchingRecords = matchingRecords;
    }

    public Map<String, FileRecord> getCsvRecordsMap() {
        return csvRecordsMap;
    }

    public void setCsvRecordsMap(Map<String, FileRecord> csvRecordsMap) {
        this.csvRecordsMap = csvRecordsMap;
    }

    public Map<String, FileRecord> getExcelRecordsMap() {
        return excelRecordsMap;
    }

    public void setExcelRecordsMap(Map<String, FileRecord> excelRecordsMap) {
        this.excelRecordsMap = excelRecordsMap;
    }
}
