package org.example;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.*;

// OpenPDF Imports
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.*;
import com.lowagie.text.Rectangle;

public class LabRecordGenerator extends JFrame {
    // UI Components
    private JTextField tfName, tfRegNo, tfYear;
    private JLabel lblLogoStatus;
    private JTable table;
    private DefaultTableModel tableModel;
    private JButton btnGenerate, btnAddExp, btnEditExp, btnDeleteExp, btnSelectLogo;
    private String logoPath = "";

    // Database Configuration
    private static final String DB_URL = "jdbc:sqlite:lab_record.db";
    private static final String PROP_FILE = "student_config.properties";

    // --- LATEX STYLE COLORS ---
    // Matches: \definecolor{codegreen}{rgb}{0,0.6,0}
    private static final Color COL_KEYWORD = new Color(148, 0, 211); // Purple-ish for keywords
    private static final Color COL_COMMENT = new Color(0, 153, 0);   // Green
    private static final Color COL_STRING = new Color(0, 0, 255);    // Blue

    // Matches: \definecolor{backcolour}{rgb}{0.95,0.95,0.92}
    private static final Color COL_CODE_BG = new Color(242, 242, 235);
    // Matches: colback=black!5 (approx)
    private static final Color COL_OUTPUT_BG = new Color(245, 245, 245);

    private static final Pattern JAVA_PATTERN = Pattern.compile(
            "//.*|/\\*[\\s\\S]*?\\*/" +
                    "|\"(?:\\\\[^\"]|[^\"\\\\])*\"" +
                    "|\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null)\\b"
    );

    // Data Structure
    static class Experiment {
        int id;
        String no, name, date, aim, code, input, output, outputImagePath;

        public Experiment(int id, String no, String name, String date, String aim, String code, String input, String output, String outputImagePath) {
            this.id = id;
            this.no = no;
            this.name = name;
            this.date = date;
            this.aim = aim;
            this.code = code;
            this.input = input;
            this.output = output;
            this.outputImagePath = outputImagePath;
        }
    }

    private ArrayList<Experiment> experiments = new ArrayList<>();

    public LabRecordGenerator() {
        setTitle("CET Lab Record Generator (Pro LaTeX Style)");
        setSize(950, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        initDatabase();

        // --- TOP PANEL ---
        JPanel pnlTop = new JPanel(new GridBagLayout());
        pnlTop.setBorder(BorderFactory.createTitledBorder("Student Details"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Name
        gbc.gridx = 0; gbc.gridy = 0; pnlTop.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; tfName = new JTextField(); pnlTop.add(tfName, gbc);

        // Reg No
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; pnlTop.add(new JLabel("Register No:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; tfRegNo = new JTextField(); pnlTop.add(tfRegNo, gbc);

        // Year
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; pnlTop.add(new JLabel("Academic Year:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0; tfYear = new JTextField(); pnlTop.add(tfYear, gbc);

        // Logo Selection
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; pnlTop.add(new JLabel("College Logo:"), gbc);
        JPanel pnlLogo = new JPanel(new BorderLayout(5, 0));
        btnSelectLogo = new JButton("Select Image...");
        lblLogoStatus = new JLabel("No logo selected");
        lblLogoStatus.setForeground(Color.RED);
        pnlLogo.add(btnSelectLogo, BorderLayout.WEST);
        pnlLogo.add(lblLogoStatus, BorderLayout.CENTER);

        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0; pnlTop.add(pnlLogo, gbc);

        add(pnlTop, BorderLayout.NORTH);

        // --- CENTER PANEL ---
        String[] columns = {"Exp No", "Experiment Name", "Date"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Experiments List"));
        add(scrollPane, BorderLayout.CENTER);

        // --- BOTTOM PANEL ---
        JPanel pnlBottom = new JPanel();
        btnAddExp = new JButton("Add New Experiment");
        btnEditExp = new JButton("Edit Experiment");
        btnDeleteExp = new JButton("Delete Experiment");
        btnGenerate = new JButton("Generate PDF Record");

        pnlBottom.add(btnAddExp);
        pnlBottom.add(btnEditExp);
        pnlBottom.add(btnDeleteExp);
        pnlBottom.add(btnGenerate);
        add(pnlBottom, BorderLayout.SOUTH);

        // --- LOAD DATA ---
        loadStudentDetails();
        loadExperimentsFromDb();

        // --- LISTENERS ---
        btnAddExp.addActionListener(e -> openExperimentDialog(null));
        btnEditExp.addActionListener(e -> editSelectedExperiment());
        btnDeleteExp.addActionListener(e -> deleteSelectedExperiment());

        btnSelectLogo.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg"));
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                logoPath = fileChooser.getSelectedFile().getAbsolutePath();
                lblLogoStatus.setText("Selected: " + fileChooser.getSelectedFile().getName());
                lblLogoStatus.setForeground(new Color(0, 100, 0));
            }
        });

        btnGenerate.addActionListener(e -> {
            saveStudentDetails();
            generatePdfFile();
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveStudentDetails();
                super.windowClosing(e);
            }
        });
    }

    // --- PDF GENERATION LOGIC ---
    private void generatePdfFile() {
        // Increased left and right margins to 70
        Document doc = new Document(PageSize.A4, 70, 70, 50, 50);
        String fileName = "Lab_Record_" + tfRegNo.getText() + ".pdf";

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
                doc.add(new com.lowagie.text.pdf.draw.LineSeparator());
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
        p.setSpacingAfter(10f); // Increased spacing after header
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
        addCenterText(content, "THIRUVANANTHAPURAM", fontLg, 20); // Reduced Space
        addCenterText(content, "DEPARTMENT OF", fontMd, 5);
        addCenterText(content, "ELECTRICAL ENGINEERING", fontMd, 30); // Reduced Space
        addCenterText(content, "LABORATORY RECORD", fontLg, 5); // Reduced Space
        addCenterText(content, "for", fontReg, 5); // Reduced Space
        addCenterText(content, "PCEOL408 OBJECT ORIENTED PROGRAMMING LAB", fontLg, 30); // Reduced Space

        // LOGO
        if (logoPath != null && !logoPath.isEmpty()) {
            try {
                Image img = Image.getInstance(logoPath);
                img.scaleToFit(100, 100);
                img.setAlignment(Element.ALIGN_CENTER);
                PdfPCell imgCell = new PdfPCell(img);
                imgCell.setBorder(0);
                imgCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                imgCell.setPaddingBottom(20f); // Reduced Space
                content.addCell(imgCell);
            } catch (Exception e) {
                // Ignore missing logo
            }
        }

        // Student Info Block (Indented like LaTeX \hspace{2cm})
        // Changed to use relative widths to reduce gap between label and value
        PdfPTable infoTable = new PdfPTable(new float[]{1f, 3f});
        infoTable.setWidthPercentage(80);
        infoTable.setHorizontalAlignment(Element.ALIGN_CENTER); // Center the block

        addInfoRow(infoTable, "Name:", tfName.getText(), fontReg);
        addInfoRow(infoTable, "Reg No:", tfRegNo.getText(), fontReg);
        addInfoRow(infoTable, "Semester:", "Fourth Semester B.Tech (ECE)", fontReg);
        addInfoRow(infoTable, "Year:", tfYear.getText(), fontReg);

        PdfPCell infoContainer = new PdfPCell(infoTable);
        infoContainer.setBorder(0);
        infoContainer.setPaddingBottom(30f); // Reduced Space
        content.addCell(infoContainer);

        // Modified Certification Statement with increased line spacing
        Paragraph certPara = new Paragraph("Certified that this is the bona fide record of work done by ______________________ in the Object Oriented Programming Lab during the academic year ______________ .", fontSm);
        certPara.setAlignment(Element.ALIGN_CENTER);
        certPara.setLeading(30f); // Reduced leading to fit on page

        // IMPORTANT: Use Composite Mode (addElement) to respect Paragraph leading
        PdfPCell certCell = new PdfPCell();
        certCell.setBorder(0);
        certCell.setPaddingBottom(40f); // Reduced Space
        certCell.addElement(certPara);
        content.addCell(certCell);

        // Signatures (Examiners Left, Faculty Right)
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

        // Add dates below signatures
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

        // Finalize Title Page
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
        cell.setGrayFill(0.9f); // Simple gray background
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
        // Sanitize string mapping non-breaking space / tabs to regular space
        if (code != null) code = code.replace("\u00A0", " ").replace("\t", "    ");

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5f); // Space above the table

        Paragraph p = new Paragraph();
        // Courier Font for code
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
                // Comment
                chunk = new Chunk(match, FontFactory.getFont(FontFactory.COURIER, 10, Font.ITALIC, COL_COMMENT));
            } else if (match.startsWith("\"")) {
                // String
                chunk = new Chunk(match, FontFactory.getFont(FontFactory.COURIER, 10, Font.NORMAL, COL_STRING));
            } else {
                // Keyword
                chunk = new Chunk(match, FontFactory.getFont(FontFactory.COURIER, 10, Font.BOLD, COL_KEYWORD));
            }
            p.add(chunk);
            lastEnd = matcher.end();
        }

        if (code != null && lastEnd < code.length()) {
            p.add(new Chunk(code.substring(lastEnd), fontBase));
        }

        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(COL_CODE_BG); // LaTeX 'backcolour'
        cell.setPadding(10f); // Increased padding
        cell.setBorderColor(Color.GRAY); // Clearer border
        cell.setBorderWidth(1f);

        table.addCell(cell);
        doc.add(table);
    }

    // --- OUTPUT BLOCK (MATCHING LATEX TCOLORBOX) ---
    private void addOutputBlock(Document doc, String output) throws DocumentException {
        // Sanitize string mapping non-breaking space / tabs to regular space
        if (output != null) output = output.replace("\u00A0", " ").replace("\t", "    ");

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5f);

        // Output text is usually plain in LaTeX verbatim
        Font outFont = FontFactory.getFont(FontFactory.COURIER, 10, Font.NORMAL, Color.BLACK);

        PdfPCell cell = new PdfPCell(new Phrase(output != null ? output : "", outFont));
        cell.setBackgroundColor(COL_OUTPUT_BG); // Light gray like LaTeX
        cell.setPadding(10f); // Increased padding
        cell.setBorderColor(Color.GRAY); // Darker border
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

    // --- DATABASE & DATA LOADING ---
    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn != null) {
                String sql = "CREATE TABLE IF NOT EXISTS experiments (id integer PRIMARY KEY AUTOINCREMENT, no text, name text, date text, aim text, code text, input text, output text);";
                try (Statement stmt = conn.createStatement()) { stmt.execute(sql); }

                // Migrate older databases by adding the input/outputImagePath columns if they don't exist
                try (Statement stmt = conn.createStatement()) { stmt.execute("ALTER TABLE experiments ADD COLUMN input text;"); } catch (SQLException ignore) {}
                try (Statement stmt = conn.createStatement()) { stmt.execute("ALTER TABLE experiments ADD COLUMN outputImagePath text;"); } catch (SQLException ignore) {}
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadExperimentsFromDb() {
        experiments.clear();
        tableModel.setRowCount(0);
        String sql = "SELECT * FROM experiments";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String inputVal = null;
                try { inputVal = rs.getString("input"); } catch (SQLException ignore) {}

                String outputImgVal = null;
                try { outputImgVal = rs.getString("outputImagePath"); } catch (SQLException ignore) {}

                Experiment exp = new Experiment(rs.getInt("id"), rs.getString("no"), rs.getString("name"), rs.getString("date"), rs.getString("aim"), rs.getString("code"), inputVal, rs.getString("output"), outputImgVal);
                experiments.add(exp);
                tableModel.addRow(new Object[]{exp.no, exp.name, exp.date});
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void saveExperimentToDb(String no, String name, String date, String aim, String code, String input, String output, String outputImagePath) {
        String sql = "INSERT INTO experiments(no, name, date, aim, code, input, output, outputImagePath) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, no); pstmt.setString(2, name); pstmt.setString(3, date); pstmt.setString(4, aim); pstmt.setString(5, code); pstmt.setString(6, input); pstmt.setString(7, output); pstmt.setString(8, outputImagePath);
            pstmt.executeUpdate();
            loadExperimentsFromDb();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void updateExperimentInDb(int id, String no, String name, String date, String aim, String code, String input, String output, String outputImagePath) {
        String sql = "UPDATE experiments SET no=?, name=?, date=?, aim=?, code=?, input=?, output=?, outputImagePath=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, no); pstmt.setString(2, name); pstmt.setString(3, date);
            pstmt.setString(4, aim); pstmt.setString(5, code); pstmt.setString(6, input); pstmt.setString(7, output); pstmt.setString(8, outputImagePath);
            pstmt.setInt(9, id);
            pstmt.executeUpdate();
            loadExperimentsFromDb();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void editSelectedExperiment() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            Experiment exp = experiments.get(selectedRow);
            openExperimentDialog(exp);
        } else {
            JOptionPane.showMessageDialog(this, "Please select an experiment to edit.");
        }
    }

    private void deleteSelectedExperiment() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            Experiment exp = experiments.get(selectedRow);
            String sql = "DELETE FROM experiments WHERE id = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, exp.id); pstmt.executeUpdate();
                loadExperimentsFromDb();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    private void loadStudentDetails() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(PROP_FILE)) {
            props.load(in);
            tfName.setText(props.getProperty("name"));
            tfRegNo.setText(props.getProperty("regNo"));
            tfYear.setText(props.getProperty("year"));
            logoPath = props.getProperty("logoPath", "");
            if(!logoPath.isEmpty()) {
                File f = new File(logoPath);
                if(f.exists()) {
                    lblLogoStatus.setText("Selected: " + f.getName());
                    lblLogoStatus.setForeground(new Color(0, 100, 0));
                }
            }
        } catch (IOException e) {
            tfName.setText("Merlin Mon Mathew"); tfRegNo.setText("TVE25EE001"); tfYear.setText("2025-26");
        }
    }

    private void saveStudentDetails() {
        Properties props = new Properties();
        props.setProperty("name", tfName.getText());
        props.setProperty("regNo", tfRegNo.getText());
        props.setProperty("year", tfYear.getText());
        if(logoPath != null) props.setProperty("logoPath", logoPath);
        try (FileOutputStream out = new FileOutputStream(PROP_FILE)) { props.store(out, null); } catch (IOException e) {}
    }

    private void openExperimentDialog(Experiment expToEdit) {
        boolean isEdit = (expToEdit != null);
        JDialog dialog = new JDialog(this, isEdit ? "Edit Experiment" : "Add Experiment", true);
        dialog.setSize(750, 700); dialog.setLayout(new GridBagLayout()); GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(5, 5, 5, 5); gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField dTfNo = new JTextField(10); JTextField dTfName = new JTextField(20); JTextField dTfDate = new JTextField(10); JTextArea dTaAim = new JTextArea(3, 40); JTextArea dTaCode = new JTextArea(10, 40); JTextArea dTaInput = new JTextArea(4, 40); JTextArea dTaOutput = new JTextArea(4, 40);

        // Components for Image Selection
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
        JButton btnSave = new JButton("Save"); gbc.gridx = 1; gbc.gridy = 8; dialog.add(btnSave, gbc);

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
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LabRecordGenerator().setVisible(true));
    }
}