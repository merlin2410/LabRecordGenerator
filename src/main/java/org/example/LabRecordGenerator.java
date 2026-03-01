package org.example;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.*;

// OpenPDF Imports
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

public class LabRecordGenerator extends JFrame {

    // --- UI Components ---
    private JTextField tfName, tfRegNo, tfYear;
    private JLabel lblLogoStatus;
    private JTable table;
    private DefaultTableModel tableModel;
    private JButton btnGenerate, btnAddExp, btnEditExp, btnDeleteExp, btnSelectLogo, btnImportLocal;
    private String logoPath = "";

    // --- Database connection ---
    private Connection conn;

    // --- Syntax Highlighting Constants ---
    private static final Color COL_KEYWORD = new Color(0, 0, 255);
    private static final Color COL_STRING = new Color(163, 21, 21);
    private static final Color COL_COMMENT = new Color(0, 128, 0);
    private static final Color COL_CODE_BG = new Color(245, 245, 245);
    private static final Color COL_OUTPUT_BG = new Color(240, 240, 240);
    private static final Pattern JAVA_PATTERN = Pattern.compile(
            "\\b(public|private|protected|class|static|void|int|double|float|char|boolean|if|else|for|while|return|new|import|package|try|catch|throws|throw|extends|implements|interface|byte|short|long|switch|case|default|break|continue|final)\\b|\".*?\"|//.*|/\\*[\\s\\S]*?\\*/"
    );

    // --- POJO for PDF Generation ---
    static class Experiment {
        int id;
        String no, name, date, aim, code, input, output, outputImagePath;

        public Experiment(int id, String no, String name, String date, String aim, String code, String input, String output, String outputImagePath) {
            this.id = id; this.no = no; this.name = name; this.date = date; this.aim = aim;
            this.code = code; this.input = input; this.output = output; this.outputImagePath = outputImagePath;
        }
    }

    public LabRecordGenerator() {
        // --- GUI SETUP ---
        setTitle("Lab Record Generator - Ultimate Edition");
        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // --- TOP PANEL (Student Details) ---
        JPanel pnlTop = new JPanel(new GridLayout(2, 4, 10, 10));
        pnlTop.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        pnlTop.add(new JLabel("Student Name:"));
        tfName = new JTextField();
        pnlTop.add(tfName);

        pnlTop.add(new JLabel("Register No:"));
        tfRegNo = new JTextField();
        pnlTop.add(tfRegNo);

        pnlTop.add(new JLabel("Year/Sem:"));
        tfYear = new JTextField();
        pnlTop.add(tfYear);

        btnSelectLogo = new JButton("Select College Logo");
        lblLogoStatus = new JLabel("No logo selected");
        lblLogoStatus.setForeground(Color.RED);
        pnlTop.add(btnSelectLogo);
        pnlTop.add(lblLogoStatus);

        add(pnlTop, BorderLayout.NORTH);

        // --- CENTER PANEL (Table) ---
        tableModel = new DefaultTableModel(new String[]{"ID", "Exp No", "Experiment Title", "Date Added"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setWidth(0);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- BOTTOM PANEL (Action Buttons) ---
        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        btnAddExp = new JButton("Add Manual Exp");
        btnEditExp = new JButton("Edit Selected");
        btnDeleteExp = new JButton("Delete Selected");
        btnImportLocal = new JButton("Import from Local Repo");
        btnGenerate = new JButton("Generate PDF Record");

        btnGenerate.setBackground(new Color(46, 204, 113));
        btnGenerate.setForeground(Color.BLACK);

        pnlBottom.add(btnAddExp);
        pnlBottom.add(btnEditExp);
        pnlBottom.add(btnDeleteExp);
        pnlBottom.add(btnImportLocal);
        pnlBottom.add(btnGenerate);

        add(pnlBottom, BorderLayout.SOUTH);

        // --- INIT DATABASE & LOAD DATA ---
        initDatabase();
        loadSettings(); // Auto-populate feature
        loadDataFromDb();

        // --- EVENT LISTENERS ---
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) { saveSettings(); }
        });

        FocusAdapter saveOnBlur = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) { saveSettings(); }
        };
        tfName.addFocusListener(saveOnBlur);
        tfRegNo.addFocusListener(saveOnBlur);
        tfYear.addFocusListener(saveOnBlur);

        btnAddExp.addActionListener(e -> openExperimentDialog(null));

        btnEditExp.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int id = (int) tableModel.getValueAt(row, 0);
                openExperimentDialog(getExperimentById(id));
            } else {
                JOptionPane.showMessageDialog(this, "Please select an experiment to edit.");
            }
        });

        btnDeleteExp.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int id = (int) tableModel.getValueAt(row, 0);
                if(JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this experiment?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    deleteExperimentFromDb(id);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select an experiment to delete.");
            }
        });

        btnImportLocal.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                importFromRepository(chooser.getSelectedFile());
            }
        });

        btnSelectLogo.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                logoPath = chooser.getSelectedFile().getAbsolutePath();
                lblLogoStatus.setText("Logo: " + chooser.getSelectedFile().getName());
                lblLogoStatus.setForeground(new Color(0, 100, 0));
                saveSettings();
            }
        });

        btnGenerate.addActionListener(e -> generatePdfFile());
    }

    // ==========================================
    // DATABASE LOGIC (SQLite)
    // ==========================================
    private void initDatabase() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:lab_records.db");
            Statement stmt = conn.createStatement();

            // Experiments table (updated to support all fields)
            String sqlExp = "CREATE TABLE IF NOT EXISTS experiments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "exp_no TEXT NOT NULL," +
                    "title TEXT NOT NULL," +
                    "date TEXT," +
                    "aim TEXT," +
                    "code TEXT," +
                    "input TEXT," +
                    "output TEXT," +
                    "image_path TEXT)";
            stmt.execute(sqlExp);

            // Settings table for Auto-populate
            String sqlSettings = "CREATE TABLE IF NOT EXISTS settings (" +
                    "key_name TEXT PRIMARY KEY, " +
                    "key_value TEXT)";
            stmt.execute(sqlSettings);

            // Migrate older databases just in case
            try { stmt.execute("ALTER TABLE experiments ADD COLUMN input text;"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE experiments ADD COLUMN image_path text;"); } catch (SQLException ignore) {}

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database connection failed: " + e.getMessage());
        }
    }

    private void saveSettings() {
        if (conn == null) return;
        String sql = "INSERT OR REPLACE INTO settings (key_name, key_value) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            saveSettingRow(pstmt, "student_name", tfName.getText());
            saveSettingRow(pstmt, "reg_no", tfRegNo.getText());
            saveSettingRow(pstmt, "year_sem", tfYear.getText());
            saveSettingRow(pstmt, "logo_path", logoPath);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveSettingRow(PreparedStatement pstmt, String key, String value) throws SQLException {
        pstmt.setString(1, key);
        pstmt.setString(2, value);
        pstmt.executeUpdate();
    }

    private void loadSettings() {
        String sql = "SELECT key_name, key_value FROM settings";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String key = rs.getString("key_name");
                String value = rs.getString("key_value");
                if (value == null) value = "";

                switch (key) {
                    case "student_name": tfName.setText(value); break;
                    case "reg_no": tfRegNo.setText(value); break;
                    case "year_sem": tfYear.setText(value); break;
                    case "logo_path":
                        logoPath = value;
                        if (!logoPath.isEmpty()) {
                            File f = new File(logoPath);
                            if(f.exists()) {
                                lblLogoStatus.setText("Logo: " + f.getName());
                                lblLogoStatus.setForeground(new Color(0, 100, 0));
                            }
                        }
                        break;
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load settings.");
        }
    }

    private void loadDataFromDb() {
        tableModel.setRowCount(0);
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, exp_no, title, date FROM experiments ORDER BY CAST(exp_no AS INTEGER)");
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("exp_no"),
                        rs.getString("title"),
                        rs.getString("date")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private Experiment getExperimentById(int id) {
        try {
            PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM experiments WHERE id = ?");
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Experiment(
                        rs.getInt("id"), rs.getString("exp_no"), rs.getString("title"),
                        rs.getString("date"), rs.getString("aim"), rs.getString("code"),
                        rs.getString("input"), rs.getString("output"), rs.getString("image_path")
                );
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private List<Experiment> getAllExperiments() {
        List<Experiment> list = new ArrayList<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM experiments ORDER BY CAST(exp_no AS INTEGER)");
            while (rs.next()) {
                list.add(new Experiment(
                        rs.getInt("id"), rs.getString("exp_no"), rs.getString("title"),
                        rs.getString("date"), rs.getString("aim"), rs.getString("code"),
                        rs.getString("input"), rs.getString("output"), rs.getString("image_path")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    private void saveExperimentToDb(String expNo, String title, String date, String aim, String code, String in, String out, String imgPath) {
        String sql = "INSERT INTO experiments(exp_no, title, date, aim, code, input, output, image_path) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expNo); pstmt.setString(2, title); pstmt.setString(3, date);
            pstmt.setString(4, aim); pstmt.setString(5, code); pstmt.setString(6, in);
            pstmt.setString(7, out); pstmt.setString(8, imgPath);
            pstmt.executeUpdate();
            loadDataFromDb();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void updateExperimentInDb(int id, String expNo, String title, String date, String aim, String code, String in, String out, String imgPath) {
        String sql = "UPDATE experiments SET exp_no=?, title=?, date=?, aim=?, code=?, input=?, output=?, image_path=? WHERE id=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expNo); pstmt.setString(2, title); pstmt.setString(3, date);
            pstmt.setString(4, aim); pstmt.setString(5, code); pstmt.setString(6, in);
            pstmt.setString(7, out); pstmt.setString(8, imgPath); pstmt.setInt(9, id);
            pstmt.executeUpdate();
            loadDataFromDb();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void deleteExperimentFromDb(int id) {
        String sql = "DELETE FROM experiments WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id); pstmt.executeUpdate();
            loadDataFromDb();
        } catch (SQLException e) { e.printStackTrace(); }
    }


    // ==========================================
    // ADD / EDIT DIALOG (GridBagLayout)
    // ==========================================
    private void openExperimentDialog(Experiment expToEdit) {
        boolean isEdit = (expToEdit != null);
        JDialog dialog = new JDialog(this, isEdit ? "Edit Experiment" : "Add Experiment", true);
        dialog.setSize(750, 700);
        dialog.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField dTfNo = new JTextField(10);
        JTextField dTfName = new JTextField(20);
        JTextField dTfDate = new JTextField(new SimpleDateFormat("dd/MM/yyyy").format(new Date()));
        JTextArea dTaAim = new JTextArea(3, 40);
        JTextArea dTaCode = new JTextArea(10, 40);
        JTextArea dTaInput = new JTextArea(4, 40);
        JTextArea dTaOutput = new JTextArea(4, 40);

        JPanel pnlOutputImage = new JPanel(new BorderLayout(5, 0));
        JButton btnSelectOutputImage = new JButton("Select Image...");
        JLabel lblOutputImageStatus = new JLabel("No image selected");
        lblOutputImageStatus.setForeground(Color.RED);
        pnlOutputImage.add(btnSelectOutputImage, BorderLayout.WEST);
        pnlOutputImage.add(lblOutputImageStatus, BorderLayout.CENTER);

        final String[] currentOutputImagePath = {null};

        if (isEdit) {
            dTfNo.setText(expToEdit.no);
            dTfName.setText(expToEdit.name);
            dTfDate.setText(expToEdit.date);
            dTaAim.setText(expToEdit.aim);
            dTaCode.setText(expToEdit.code);
            dTaInput.setText(expToEdit.input);
            dTaOutput.setText(expToEdit.output);
            currentOutputImagePath[0] = expToEdit.outputImagePath;

            if (currentOutputImagePath[0] != null && !currentOutputImagePath[0].isEmpty()) {
                File f = new File(currentOutputImagePath[0]);
                if (f.exists()) {
                    lblOutputImageStatus.setText("Selected: " + f.getName());
                    lblOutputImageStatus.setForeground(new Color(0, 100, 0));
                }
            }
        } else {
            dTfNo.setText(String.valueOf(tableModel.getRowCount() + 1));
        }

        btnSelectOutputImage.addActionListener(ev -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg"));
            if (fileChooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                currentOutputImagePath[0] = fileChooser.getSelectedFile().getAbsolutePath();
                lblOutputImageStatus.setText("Selected: " + fileChooser.getSelectedFile().getName());
                lblOutputImageStatus.setForeground(new Color(0, 100, 0));
            }
        });

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("Exp No:"), gbc); gbc.gridx = 1; dialog.add(dTfNo, gbc);
        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("Exp Name:"), gbc); gbc.gridx = 1; dialog.add(dTfName, gbc);
        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Date:"), gbc); gbc.gridx = 1; dialog.add(dTfDate, gbc);
        gbc.gridx = 0; gbc.gridy = 3; dialog.add(new JLabel("Aim:"), gbc); gbc.gridx = 1; dialog.add(new JScrollPane(dTaAim), gbc);
        gbc.gridx = 0; gbc.gridy = 4; dialog.add(new JLabel("Code:"), gbc); gbc.gridx = 1; dialog.add(new JScrollPane(dTaCode), gbc);
        gbc.gridx = 0; gbc.gridy = 5; dialog.add(new JLabel("Input (Optional):"), gbc); gbc.gridx = 1; dialog.add(new JScrollPane(dTaInput), gbc);
        gbc.gridx = 0; gbc.gridy = 6; dialog.add(new JLabel("Output (Text):"), gbc); gbc.gridx = 1; dialog.add(new JScrollPane(dTaOutput), gbc);
        gbc.gridx = 0; gbc.gridy = 7; dialog.add(new JLabel("Output (GUI/Image):"), gbc); gbc.gridx = 1; dialog.add(pnlOutputImage, gbc);

        JButton btnSave = new JButton("Save Experiment");
        gbc.gridx = 1; gbc.gridy = 8;
        dialog.add(btnSave, gbc);

        btnSave.addActionListener(ev -> {
            // Sanitize all rich text components prior to pushing them to DB
            String sanitizedAim = dTaAim.getText().replace("\u00A0", " ").replace("\t", "    ");
            String sanitizedCode = dTaCode.getText().replace("\u00A0", " ").replace("\t", "    ");
            String sanitizedInput = dTaInput.getText().replace("\u00A0", " ").replace("\t", "    ");
            String sanitizedOutput = dTaOutput.getText().replace("\u00A0", " ").replace("\t", "    ");

            if (isEdit) {
                updateExperimentInDb(expToEdit.id, dTfNo.getText(), dTfName.getText(), dTfDate.getText(), sanitizedAim, sanitizedCode, sanitizedInput, sanitizedOutput, currentOutputImagePath[0]);
            } else {
                saveExperimentToDb(dTfNo.getText(), dTfName.getText(), dTfDate.getText(), sanitizedAim, sanitizedCode, sanitizedInput, sanitizedOutput, currentOutputImagePath[0]);
            }
            dialog.dispose();
        });
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }


    // ==========================================
    // LOCAL REPOSITORY IMPORT LOGIC
    // ==========================================
    public void importFromRepository(File repoDir) {
        String expName = "Imported Experiment";
        String expNo = String.valueOf(tableModel.getRowCount() + 1);

        File readmeFile = new File(repoDir, "README.md");
        if (!readmeFile.exists()) readmeFile = new File(repoDir, "readme.md");

        if (readmeFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(readmeFile))) {
                String line;
                Pattern pattern = Pattern.compile("(?i)#\\s*Experiment\\s+(\\d+)[\\s:\\-]*(.*)");

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        expNo = matcher.group(1).trim();
                        expName = matcher.group(2).trim();
                        break;
                    } else if (line.startsWith("# ") && !line.toLowerCase().contains("![review assignment")) {
                        expName = line.replace("#", "").trim();
                        break;
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        File autogradingFile = new File(repoDir, ".github/classroom/autograding.json");
        String input = "", output = "", runCmd = "";

        if (autogradingFile.exists()) {
            try {
                String jsonContent = new String(Files.readAllBytes(autogradingFile.toPath()));
                String[] blocks = jsonContent.split("\\{");

                for (String block : blocks) {
                    if (block.contains("\"name\"") && block.contains("\"run\"")) {
                        runCmd = extractJsonValue(block, "run");
                        input = extractJsonValue(block, "input");
                        output = extractJsonValue(block, "output");
                        break;
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        File srcDir = new File(repoDir, "src");
        String code = "// Could not find 'src' folder or .java files.";

        if (srcDir.exists() && srcDir.isDirectory()) {
            String className = "";
            if (runCmd != null && !runCmd.isEmpty()) {
                String[] parts = runCmd.trim().split(" ");
                className = parts[parts.length - 1].replace(".java", "").trim();
                if (className.contains(".")) className = className.substring(className.lastIndexOf(".") + 1);
            }

            File javaFile = (!className.isEmpty()) ? findJavaFile(srcDir, className) : null;
            if (javaFile == null) javaFile = findFirstJavaFile(srcDir);

            if (javaFile != null && javaFile.exists()) {
                try { code = new String(Files.readAllBytes(javaFile.toPath())); }
                catch (Exception e) { code = "// Error reading Java file."; }
            } else { code = "// Could not find the main Java file in the src directory."; }
        }

        String date = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
        String aim = "To implement " + expName;

        saveExperimentToDb(expNo, expName, date, aim, code, input, output, "");
        JOptionPane.showMessageDialog(this, "Successfully imported Experiment " + expNo + ": '" + expName + "'!", "Import Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private String extractJsonValue(String jsonBlock, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(jsonBlock);
        if (matcher.find()) { return matcher.group(1).replace("\\n", "\n").replace("\\r", "\r"); }
        return "";
    }

    private File findJavaFile(File dir, String className) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File found = findJavaFile(file, className);
                    if (found != null) return found;
                } else if (file.getName().equals(className + ".java")) return file;
            }
        }
        return null;
    }

    private File findFirstJavaFile(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File found = findFirstJavaFile(file);
                    if (found != null) return found;
                } else if (file.getName().endsWith(".java")) return file;
            }
        }
        return null;
    }

    // ==========================================
    // PDF GENERATION (Aesthetic Upgrade)
    // ==========================================
    private void generatePdfFile() {
        if (tfName.getText().isEmpty() || tfRegNo.getText().isEmpty() || tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Please fill student details and add at least one experiment.");
            return;
        }

        saveSettings();
        List<Experiment> experiments = getAllExperiments();

        // Increased left and right margins to 70
        Document doc = new Document(PageSize.A4, 70, 70, 50, 50);
        String desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
        String fileName = desktopPath + File.separator + "Lab_Record_" + tfRegNo.getText() + ".pdf";

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(fileName));

            // Header: OOP (Java) Lab Record - Name
            String headerText = "OOP (Java) Lab Record - " + tfName.getText();
            HeaderFooterPageEvent event = new HeaderFooterPageEvent(headerText);
            writer.setPageEvent(event);

            doc.open();

            // 1. TITLE PAGE
            addTitlePage(doc);

            // 2. INDEX PAGE
            doc.newPage();
            Paragraph indexTitle = new Paragraph("INDEX", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            indexTitle.setAlignment(Element.ALIGN_CENTER);
            doc.add(indexTitle);
            doc.add(new Paragraph("\n"));

            // Index Table
            PdfPTable indexTable = new PdfPTable(new float[]{1, 5, 2.5f, 2});
            indexTable.setWidthPercentage(100);
            indexTable.setHeaderRows(1);

            addHeaderCell(indexTable, "No.");
            addHeaderCell(indexTable, "Experiment Name");
            addHeaderCell(indexTable, "Date");
            addHeaderCell(indexTable, "Sign");

            for (Experiment exp : experiments) {
                addCell(indexTable, exp.no, Element.ALIGN_CENTER);
                addCell(indexTable, exp.name, Element.ALIGN_LEFT);
                addCell(indexTable, exp.date, Element.ALIGN_CENTER);
                addCell(indexTable, "", Element.ALIGN_CENTER);
            }
            doc.add(indexTable);

            // 3. EXPERIMENTS LOOP
            for (Experiment exp : experiments) {
                doc.newPage();

                // Header style: Experiment 1: Name
                Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
                Paragraph expTitle = new Paragraph("Experiment " + exp.no + ": " + exp.name, titleFont);
                expTitle.setSpacingAfter(8f);
                doc.add(expTitle);

                Paragraph datePara = new Paragraph("Date: " + exp.date, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
                datePara.setSpacingAfter(20f);
                doc.add(datePara);

                // Aim
                addSectionHeader(doc, "Aim");
                Paragraph aimText = new Paragraph(exp.aim, FontFactory.getFont(FontFactory.HELVETICA, 11));
                aimText.setSpacingAfter(15f);
                doc.add(aimText);

                // Code
                addSectionHeader(doc, "Program Code");
                addHighlightedCodeBlock(doc, exp.code);
                doc.add(new Paragraph("\n"));

                // Input (Optional)
                if (exp.input != null && !exp.input.trim().isEmpty()) {
                    addSectionHeader(doc, "Input");
                    addOutputBlock(doc, exp.input);
                    doc.add(new Paragraph("\n"));
                }

                // Output (Text & Image)
                addSectionHeader(doc, "Output");

                // Text Output
                if (exp.output != null && !exp.output.trim().isEmpty()) {
                    addOutputBlock(doc, exp.output);
                }

                // Image Output (for Swing/GUI)
                if (exp.outputImagePath != null && !exp.outputImagePath.trim().isEmpty()) {
                    try {
                        Image img = Image.getInstance(exp.outputImagePath);

                        // Scale to fit page width, leaving some margin
                        float documentWidth = doc.getPageSize().getWidth() - doc.leftMargin() - doc.rightMargin();
                        if (img.getWidth() > documentWidth) {
                            img.scaleToFit(documentWidth, doc.getPageSize().getHeight() - doc.topMargin() - doc.bottomMargin());
                        }

                        img.setBorder(Rectangle.BOX);
                        img.setBorderWidth(1f);
                        img.setBorderColor(Color.GRAY);
                        img.setAlignment(Element.ALIGN_CENTER);
                        img.setSpacingBefore(10f);
                        img.setSpacingAfter(10f);

                        doc.add(img);
                    } catch (Exception ex) {
                        System.err.println("Could not load output image: " + exp.outputImagePath);
                    }
                }

                // Separator
                doc.add(new Paragraph("\n"));
                doc.add(new LineSeparator());
            }

            doc.close();
            JOptionPane.showMessageDialog(this, "PDF Generated Successfully: " + fileName);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(fileName));
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error generating PDF: " + e.getMessage());
        }
    }

    private void addSectionHeader(Document doc, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        p.setSpacingAfter(10f);
        doc.add(p);
    }

    // --- TITLE PAGE (LATEX MATCHING) ---
    private void addTitlePage(Document doc) throws DocumentException {
        // Outer border table
        PdfPTable borderTable = new PdfPTable(1);
        borderTable.setWidthPercentage(100);
        // Simulating tcolorbox frame
        borderTable.getDefaultCell().setBorder(Rectangle.BOX);
        borderTable.getDefaultCell().setBorderWidth(2f);
        borderTable.getDefaultCell().setPadding(20f);
        borderTable.getDefaultCell().setMinimumHeight(doc.getPageSize().getHeight() - 120);

        PdfPTable content = new PdfPTable(1);
        content.setWidthPercentage(100);

        Font fontLg = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font fontMd = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
        Font fontReg = FontFactory.getFont(FontFactory.HELVETICA, 12);
        Font fontSm = FontFactory.getFont(FontFactory.HELVETICA, 10);

        addCenterText(content, "COLLEGE OF ENGINEERING", fontLg, 5);
        addCenterText(content, "THIRUVANANTHAPURAM", fontLg, 20);
        addCenterText(content, "DEPARTMENT OF", fontMd, 5);
        addCenterText(content, "ELECTRICAL ENGINEERING", fontMd, 30);
        addCenterText(content, "LABORATORY RECORD", fontLg, 5);
        addCenterText(content, "for", fontReg, 5);
        addCenterText(content, "PCEOL408 OBJECT ORIENTED PROGRAMMING LAB", fontLg, 30);

        // LOGO
        if (logoPath != null && !logoPath.isEmpty()) {
            try {
                Image img = Image.getInstance(logoPath);
                img.scaleToFit(100, 100);
                img.setAlignment(Element.ALIGN_CENTER);
                PdfPCell imgCell = new PdfPCell(img);
                imgCell.setBorder(0);
                imgCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                imgCell.setPaddingBottom(20f);
                content.addCell(imgCell);
            } catch (Exception e) {
                // Ignore missing logo
            }
        }

        PdfPTable infoTable = new PdfPTable(new float[]{1f, 3f});
        infoTable.setWidthPercentage(80);
        infoTable.setHorizontalAlignment(Element.ALIGN_CENTER);

        addInfoRow(infoTable, "Name:", tfName.getText(), fontReg);
        addInfoRow(infoTable, "Reg No:", tfRegNo.getText(), fontReg);
        addInfoRow(infoTable, "Semester:", "Fourth Semester B.Tech (Electrical and Computer Engineering)", fontReg);
        addInfoRow(infoTable, "Year:", tfYear.getText(), fontReg);

        PdfPCell infoContainer = new PdfPCell(infoTable);
        infoContainer.setBorder(0);
        infoContainer.setPaddingBottom(30f);
        content.addCell(infoContainer);

        Paragraph certPara = new Paragraph("Certified that this is the bona fide record of work done by ______________________ in the Object Oriented Programming Lab during the academic year ______________ .", fontSm);
        certPara.setAlignment(Element.ALIGN_CENTER);
        certPara.setLeading(30f);

        PdfPCell certCell = new PdfPCell();
        certCell.setBorder(0);
        certCell.setPaddingBottom(40f);
        certCell.addElement(certPara);
        content.addCell(certCell);

        PdfPTable signTable = new PdfPTable(2);
        signTable.setWidthPercentage(100);

        PdfPCell signLeft = new PdfPCell(new Paragraph("Examiners", fontReg));
        signLeft.setBorder(0);
        signLeft.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell signRight = new PdfPCell(new Paragraph("Faculty in charge", fontReg));
        signRight.setBorder(0);
        signRight.setHorizontalAlignment(Element.ALIGN_RIGHT);

        signTable.addCell(signLeft);
        signTable.addCell(signRight);

        PdfPCell dateLeft = new PdfPCell(new Paragraph("\n\nThiruvananthapuram\nDate: ____________", fontSm));
        dateLeft.setBorder(0);
        dateLeft.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell dateRight = new PdfPCell(new Paragraph("", fontSm));
        dateRight.setBorder(0);

        signTable.addCell(dateLeft);
        signTable.addCell(dateRight);

        PdfPCell signContainer = new PdfPCell(signTable);
        signContainer.setBorder(0);
        content.addCell(signContainer);

        PdfPCell containerCell = new PdfPCell(content);
        containerCell.setBorder(0);
        borderTable.addCell(containerCell);

        doc.add(borderTable);
    }

    private void addCenterText(PdfPTable table, String text, Font font, float spaceAfter) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorder(0);
        cell.setPaddingBottom(spaceAfter);
        table.addCell(cell);
    }

    private void addInfoRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        c1.setBorder(0);
        c1.setPaddingBottom(8f);

        PdfPCell c2 = new PdfPCell(new Phrase(value, font));
        c2.setBorder(0);
        c2.setPaddingBottom(8f);

        table.addCell(c1);
        table.addCell(c2);
    }

    // --- TABLE HEADERS ---
    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setGrayFill(0.9f);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    // --- HIGHLIGHTED CODE (MATCHING LATEX LISTINGS) ---
    private void addHighlightedCodeBlock(Document doc, String code) throws DocumentException {
        if (code != null) code = code.replace("\u00A0", " ").replace("\t", "    ");

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5f);

        Paragraph p = new Paragraph();
        Font fontBase = FontFactory.getFont(FontFactory.COURIER, 10, Font.NORMAL, Color.BLACK);
        p.setFont(fontBase);

        Matcher matcher = JAVA_PATTERN.matcher(code != null ? code : "");
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                p.add(new Chunk(code.substring(lastEnd, matcher.start()), fontBase));
            }

            String match = matcher.group();
            Chunk chunk;

            if (match.startsWith("//") || match.startsWith("/*")) {
                chunk = new Chunk(match, FontFactory.getFont(FontFactory.COURIER, 10, Font.ITALIC, COL_COMMENT));
            } else if (match.startsWith("\"")) {
                chunk = new Chunk(match, FontFactory.getFont(FontFactory.COURIER, 10, Font.NORMAL, COL_STRING));
            } else {
                chunk = new Chunk(match, FontFactory.getFont(FontFactory.COURIER, 10, Font.BOLD, COL_KEYWORD));
            }
            p.add(chunk);
            lastEnd = matcher.end();
        }

        if (code != null && lastEnd < code.length()) {
            p.add(new Chunk(code.substring(lastEnd), fontBase));
        }

        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(COL_CODE_BG);
        cell.setPadding(10f);
        cell.setBorderColor(Color.GRAY);
        cell.setBorderWidth(1f);

        table.addCell(cell);
        doc.add(table);
    }

    // --- OUTPUT BLOCK (MATCHING LATEX TCOLORBOX) ---
    private void addOutputBlock(Document doc, String output) throws DocumentException {
        if (output != null) output = output.replace("\u00A0", " ").replace("\t", "    ");

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5f);

        Font outFont = FontFactory.getFont(FontFactory.COURIER, 10, Font.NORMAL, Color.BLACK);

        PdfPCell cell = new PdfPCell(new Phrase(output != null ? output : "", outFont));
        cell.setBackgroundColor(COL_OUTPUT_BG);
        cell.setPadding(10f);
        cell.setBorderColor(Color.GRAY);
        cell.setBorderWidth(1f);

        table.addCell(cell);
        doc.add(table);
    }

    // --- PAGE NUMBERS ---
    class HeaderFooterPageEvent extends PdfPageEventHelper {
        String header;
        Font font = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9);

        public HeaderFooterPageEvent(String header) { this.header = header; }

        public void onEndPage(PdfWriter writer, Document document) {
            if(writer.getPageNumber() == 1) return;
            PdfContentByte cb = writer.getDirectContent();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(header, font),
                    document.left(), document.top() + 10, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Page " + writer.getPageNumber(), font),
                    document.right(), document.bottom() - 10, 0);
        }
    }

    // ==========================================
    // MAIN METHOD
    // ==========================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new LabRecordGenerator().setVisible(true);
        });
    }
}