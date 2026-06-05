package utilities;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.sl.usermodel.Sheet;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.*;



public class ReadExcelFile {

    public static java.io.InputStream fileinputstream;
    public static XSSFWorkbook workbook;
    public static XSSFSheet sheet;
    public static XSSFRow row;
    public static XSSFCell cell;

    // Method to get cell value using sheet name
    public static String getCellValue(String filename, String sheetName, int rowNo, int colNo) {
        java.io.InputStream is = null;
        XSSFWorkbook workbook = null;

        try {
            is = getInputStream(filename);
            workbook = new XSSFWorkbook(is);
            XSSFSheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) return null;

            XSSFRow row = sheet.getRow(rowNo);
            if (row == null) return null;

            XSSFCell cell = row.getCell(colNo);
            if (cell == null) return null;

            // Switch based on cell type
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString(); // Format if needed
                    } else {
                        return String.valueOf(cell.getNumericCellValue());
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    return cell.getCellFormula(); // Evaluate if needed
                case BLANK:
                    return null;
                default:
                    return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (workbook != null) workbook.close();
                if (is != null) is.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public static int getRowCount(String filename, String sheetName) throws IOException {
        try {
            fileinputstream = getInputStream(filename);
            System.out.println("Excel file path/resource is = " + filename);
            workbook = new XSSFWorkbook(fileinputstream);

            sheet = workbook.getSheet(sheetName);
            if (sheet == null) throw new IllegalArgumentException("Sheet not found: " + sheetName);
            System.out.println("Sheet name is = " + sheetName);

            int rowCount = sheet.getLastRowNum() + 1;
            workbook.close();
            return rowCount;
        } catch (Exception e) {
            System.err.println("❌ Exception occurred while opening workbook:");
            e.printStackTrace();
            throw e;
        }
    }

    public static int getColumnCount(String filename, String sheetName) throws IOException {
        fileinputstream = getInputStream(filename);
        workbook = new XSSFWorkbook(fileinputstream);

        sheet = workbook.getSheet(sheetName);
        if (sheet == null) throw new IllegalArgumentException("Sheet not found: " + sheetName);

        row = sheet.getRow(0);
        if (row == null) return 0;

        int colCount = row.getLastCellNum();
        workbook.close();
        return colCount;
    }
    
    
    
    //method to read all data from a specific column and ignoring header of the column 
    
    public static String[] getColumnData(String filePath, String sheetName, int columnIndex) {
        List<String> data = new ArrayList<>();

        try (java.io.InputStream fis = getInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            XSSFSheet sheet = workbook.getSheet(sheetName);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Skip header row
                XSSFRow row = sheet.getRow(i);
                if (row != null) {
                    XSSFCell cell = row.getCell(columnIndex);
                    if (cell != null) {
                        data.add(getCellValueAsString(cell));
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Error reading Excel: " + e.getMessage());
        }

        return data.toArray(new String[0]);
    }

    private static String getCellValueAsString(XSSFCell cell) {
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell))
                    return cell.getDateCellValue().toString(); // You may format it
                else
                    return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            case BLANK: return "";
            default: return cell.toString();
        }
    }
    
    
    
    
    
    
    
    
    
    
    private static java.io.InputStream getInputStream(String filename) throws IOException {
        String resourceName = filename;
        if (resourceName.startsWith("src/main/resources/")) {
            resourceName = resourceName.substring("src/main/resources/".length());
        } else if (resourceName.startsWith("src/test/resources/")) {
            resourceName = resourceName.substring("src/test/resources/".length());
        }
        
        java.io.InputStream is = ReadExcelFile.class.getClassLoader().getResourceAsStream(resourceName);
        if (is == null) {
            is = ReadExcelFile.class.getClassLoader().getResourceAsStream("resources/" + resourceName);
        }
        if (is == null) {
            is = new FileInputStream(filename);
        }
        return is;
    }
}
