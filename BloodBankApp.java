import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class BloodBankApp {

    // CSV file paths (in working directory)
    private static final Path DONORS_CSV = Paths.get("donors.csv");
    private static final Path INVENTORY_CSV = Paths.get("inventory.csv");
    private static final Path REQUESTS_CSV = Paths.get("requests.csv");

    // Date format
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // In-memory data
    private final List<Donor> donors = new ArrayList<>();
    private final Map<String, Integer> inventory = new TreeMap<>(); // blood type -> units
    private final List<Request> requests = new ArrayList<>();

    // Swing UI components
    private JFrame frame;

    // Donors tab components
    private DefaultTableModel donorsModel;
    private JTable donorsTable;
    private JTextField donorNameField, donorContactField, donorAgeField;
    private JComboBox<String> donorBloodCombo;
    private JSpinner donorDateSpinner;
    private JTextField donorSearchField;

    // Inventory tab components
    private DefaultTableModel inventoryModel;
    private JTable inventoryTable;
    private JComboBox<String> inventoryTypeCombo;
    private JSpinner inventoryUnitsSpinner;

    // Requests tab components
    private DefaultTableModel requestModel;
    private JTable requestTable;
    private JComboBox<String> reqBloodCombo;
    private JSpinner reqUnitsSpinner;
    private JTextField reqNameField;

    // Blood types
    private static final String[] BLOOD_TYPES = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};

    // Models
    static class Donor {
        String id;
        String name;
        String bloodType;
        int age;
        String contact;
        LocalDate lastDonation;

        Donor(String id, String name, String bloodType, int age, String contact, LocalDate lastDonation) {
            this.id = id; this.name = name; this.bloodType = bloodType; this.age = age; this.contact = contact; this.lastDonation = lastDonation;
        }
    }

    static class Request {
        String id;
        String requester;
        String bloodType;
        int units;
        String status; // "Pending", "Fulfilled", "Cancelled"

        Request(String id, String requester, String bloodType, int units, String status) {
            this.id = id; this.requester = requester; this.bloodType = bloodType; this.units = units; this.status = status;
        }
    }

    // Constructor
    public BloodBankApp() {
        initInventoryDefault();
        loadAllData();
        SwingUtilities.invokeLater(this::buildUI);
    }

    // Initialize inventory map with zero values if missing
    private void initInventoryDefault() {
        for (String bt : BLOOD_TYPES) inventory.put(bt, 0);
    }

    // ---------- Persistence (CSV) ----------
    private void loadAllData() {
        loadDonors();
        loadInventory();
        loadRequests();
    }

    private void saveAllData() {
        saveDonors();
        saveInventory();
        saveRequests();
    }

    private void loadDonors() {
        donors.clear();
        if (!Files.exists(DONORS_CSV)) return;
        try {
            List<String> lines = Files.readAllLines(DONORS_CSV, StandardCharsets.UTF_8);
            for (String ln : lines) {
                if (ln.trim().isEmpty()) continue;
                // id,name,blood,age,contact,lastDonation
                String[] p = ln.split(",", -1);
                if (p.length < 6) continue;
                donors.add(new Donor(p[0], p[1], p[2], Integer.parseInt(p[3]), p[4], LocalDate.parse(p[5], DF)));
            }
        } catch (Exception e) {
            showError("Failed to load donors: " + e.getMessage());
        }
    }

    private void saveDonors() {
        try {
            List<String> lines = donors.stream()
                    .map(d -> String.join(",", d.id, escape(d.name), d.bloodType, String.valueOf(d.age), escape(d.contact), d.lastDonation.format(DF)))
                    .collect(Collectors.toList());
            Files.write(DONORS_CSV, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            showError("Failed to save donors: " + e.getMessage());
        }
    }

    private void loadInventory() {
        // start with default zero-filled map
        initInventoryDefault();
        if (!Files.exists(INVENTORY_CSV)) return;
        try {
            List<String> lines = Files.readAllLines(INVENTORY_CSV, StandardCharsets.UTF_8);
            for (String ln : lines) {
                if (ln.trim().isEmpty()) continue;
                String[] p = ln.split(",", -1);
                if (p.length < 2) continue;
                String type = p[0];
                int units = Integer.parseInt(p[1]);
                inventory.put(type, units);
            }
        } catch (Exception e) {
            showError("Failed to load inventory: " + e.getMessage());
        }
    }

    private void saveInventory() {
        try {
            List<String> lines = inventory.entrySet().stream()
                    .map(e -> e.getKey() + "," + e.getValue())
                    .collect(Collectors.toList());
            Files.write(INVENTORY_CSV, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            showError("Failed to save inventory: " + e.getMessage());
        }
    }

    private void loadRequests() {
        requests.clear();
        if (!Files.exists(REQUESTS_CSV)) return;
        try {
            List<String> lines = Files.readAllLines(REQUESTS_CSV, StandardCharsets.UTF_8);
            for (String ln : lines) {
                if (ln.trim().isEmpty()) continue;
                // id,requester,blood,units,status
                String[] p = ln.split(",", -1);
                if (p.length < 5) continue;
                requests.add(new Request(p[0], p[1], p[2], Integer.parseInt(p[3]), p[4]));
            }
        } catch (Exception e) {
            showError("Failed to load requests: " + e.getMessage());
        }
    }

    private void saveRequests() {
        try {
            List<String> lines = requests.stream()
                    .map(r -> String.join(",", r.id, escape(r.requester), r.bloodType, String.valueOf(r.units), r.status))
                    .collect(Collectors.toList());
            Files.write(REQUESTS_CSV, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            showError("Failed to save requests: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace(",", " ");
    }

    // ---------- UI ----------

    private void buildUI() {
        frame = new JFrame("Blood Bank Management System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Donors", buildDonorsPanel());
        tabs.add("Inventory", buildInventoryPanel());
        tabs.add("Requests", buildRequestsPanel());
        tabs.add("Reports", buildReportsPanel());

        frame.getContentPane().add(tabs);
        frame.setVisible(true);

        // refresh models
        refreshDonorsModel();
        refreshInventoryModel();
        refreshRequestsModel();
    }

    // ---------- Donors Panel ----------
    private JPanel buildDonorsPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        p.setBorder(new EmptyBorder(8,8,8,8));

        donorsModel = new DefaultTableModel(new Object[]{"ID","Name","Blood","Age","Contact","LastDonation"}, 0) {
            public boolean isCellEditable(int r,int c){return false;}
        };
        donorsTable = new JTable(donorsModel);
        donorsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(donorsTable);
        p.add(scroll, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6,6,6,6);
        g.gridx = 0; g.gridy = 0; form.add(new JLabel("Name:"), g);
        g.gridx = 1; donorNameField = new JTextField(14); form.add(donorNameField, g);
        g.gridx = 0; g.gridy = 1; form.add(new JLabel("Blood Type:"), g);
        g.gridx = 1; donorBloodCombo = new JComboBox<>(BLOOD_TYPES); form.add(donorBloodCombo, g);
        g.gridx = 0; g.gridy = 2; form.add(new JLabel("Age:"), g);
        g.gridx = 1; donorAgeField = new JTextField(4); form.add(donorAgeField, g);
        g.gridx = 0; g.gridy = 3; form.add(new JLabel("Contact:"), g);
        g.gridx = 1; donorContactField = new JTextField(10); form.add(donorContactField, g);
        g.gridx = 0; g.gridy = 4; form.add(new JLabel("Last Donation:"), g);
        g.gridx = 1;
        donorDateSpinner = new JSpinner(new SpinnerDateModel());
        donorDateSpinner.setEditor(new JSpinner.DateEditor(donorDateSpinner, "yyyy-MM-dd"));
        form.add(donorDateSpinner, g);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8,0));
        JButton addBtn = new JButton("Add Donor");
        JButton editBtn = new JButton("Edit Selected");
        JButton delBtn = new JButton("Delete Selected");
        btnRow.add(addBtn); btnRow.add(editBtn); btnRow.add(delBtn);

        g.gridx = 0; g.gridy = 5; g.gridwidth = 2; form.add(btnRow, g);

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        donorSearchField = new JTextField(18);
        JButton searchBtn = new JButton("Search");
        JButton clearSearch = new JButton("Clear");
        searchRow.add(new JLabel("Search by name or blood:"));
        searchRow.add(donorSearchField);
        searchRow.add(searchBtn);
        searchRow.add(clearSearch);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(form, BorderLayout.WEST);
        bottom.add(searchRow, BorderLayout.SOUTH);

        p.add(bottom, BorderLayout.SOUTH);

        // Actions
        addBtn.addActionListener(e -> addDonor());
        editBtn.addActionListener(e -> editSelectedDonor());
        delBtn.addActionListener(e -> deleteSelectedDonor());
        searchBtn.addActionListener(e -> applyDonorFilter());
        clearSearch.addActionListener(e -> { donorSearchField.setText(""); refreshDonorsModel(); });

        donorsTable.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent me){
                if (me.getClickCount()==2) loadSelectedDonorToForm();
            }
        });

        return p;
    }

    private void addDonor() {
        String name = donorNameField.getText().trim();
        String blood = (String) donorBloodCombo.getSelectedItem();
        String ageStr = donorAgeField.getText().trim();
        String contact = donorContactField.getText().trim();
        Date dt = (Date) donorDateSpinner.getValue();
        LocalDate ld = LocalDate.ofInstant(dt.toInstant(), java.time.ZoneId.systemDefault());
        if (name.isEmpty() || ageStr.isEmpty()) { showError("Name and Age are required."); return; }
        int age;
        try { age = Integer.parseInt(ageStr); } catch (NumberFormatException ex) { showError("Invalid age."); return; }
        String id = "D" + (donors.size() + 1) + "_" + System.currentTimeMillis()%10000;
        donors.add(new Donor(id, name, blood, age, contact, ld));
        saveDonors();
        refreshDonorsModel();
        clearDonorForm();
    }

    private void editSelectedDonor() {
        int r = donorsTable.getSelectedRow();
        if (r == -1) { showError("Select a donor to edit."); return; }
        int mr = donorsTable.convertRowIndexToModel(r);
        Donor d = donors.get(mr);
        String name = donorNameField.getText().trim();
        String blood = (String) donorBloodCombo.getSelectedItem();
        String ageStr = donorAgeField.getText().trim();
        String contact = donorContactField.getText().trim();
        Date dt = (Date) donorDateSpinner.getValue();
        LocalDate ld = LocalDate.ofInstant(dt.toInstant(), java.time.ZoneId.systemDefault());
        if (name.isEmpty() || ageStr.isEmpty()) { showError("Name and Age are required."); return; }
        int age;
        try { age = Integer.parseInt(ageStr); } catch (NumberFormatException ex) { showError("Invalid age."); return; }
        d.name = name; d.bloodType = blood; d.age = age; d.contact = contact; d.lastDonation = ld;
        saveDonors(); refreshDonorsModel(); clearDonorForm();
    }

    private void deleteSelectedDonor() {
        int r = donorsTable.getSelectedRow();
        if (r == -1) { showError("Select a donor to delete."); return; }
        int mr = donorsTable.convertRowIndexToModel(r);
        Donor d = donors.get(mr);
        int yn = JOptionPane.showConfirmDialog(frame, "Delete donor " + d.name + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (yn == JOptionPane.YES_OPTION) {
            donors.remove(mr);
            saveDonors(); refreshDonorsModel();
        }
    }

    private void loadSelectedDonorToForm() {
        int r = donorsTable.getSelectedRow(); if (r == -1) return;
        int mr = donorsTable.convertRowIndexToModel(r);
        Donor d = donors.get(mr);
        donorNameField.setText(d.name);
        donorBloodCombo.setSelectedItem(d.bloodType);
        donorAgeField.setText(String.valueOf(d.age));
        donorContactField.setText(d.contact);
        donorDateSpinner.setValue(java.sql.Date.valueOf(d.lastDonation));
    }

    private void refreshDonorsModel() {
        donorsModel.setRowCount(0);
        for (Donor d: donors) {
            donorsModel.addRow(new Object[]{d.id, d.name, d.bloodType, d.age, d.contact, d.lastDonation.format(DF)});
        }
    }

    private void applyDonorFilter() {
        String q = donorSearchField.getText().trim().toLowerCase();
        donorsModel.setRowCount(0);
        for (Donor d: donors) {
            if (d.name.toLowerCase().contains(q) || d.bloodType.toLowerCase().contains(q)) {
                donorsModel.addRow(new Object[]{d.id, d.name, d.bloodType, d.age, d.contact, d.lastDonation.format(DF)});
            }
        }
    }

    private void clearDonorForm() {
        donorNameField.setText(""); donorAgeField.setText(""); donorContactField.setText("");
        donorBloodCombo.setSelectedIndex(0);
        donorDateSpinner.setValue(new Date());
    }

    // ---------- Inventory Panel ----------
    private JPanel buildInventoryPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        p.setBorder(new EmptyBorder(8,8,8,8));

        inventoryModel = new DefaultTableModel(new Object[]{"Blood Type","Units"},0) {
            public boolean isCellEditable(int r,int c){ return false;}
        };
        inventoryTable = new JTable(inventoryModel);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        inventoryTypeCombo = new JComboBox<>(BLOOD_TYPES);
        inventoryUnitsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
        JButton addUnitsBtn = new JButton("Add Units");
        JButton removeUnitsBtn = new JButton("Remove Units");
        JButton saveInvBtn = new JButton("Save Inventory");
        top.add(new JLabel("Blood Type:")); top.add(inventoryTypeCombo);
        top.add(new JLabel("Units:")); top.add(inventoryUnitsSpinner);
        top.add(addUnitsBtn); top.add(removeUnitsBtn); top.add(saveInvBtn);

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(inventoryTable), BorderLayout.CENTER);

        addUnitsBtn.addActionListener(e -> modifyInventory(true));
        removeUnitsBtn.addActionListener(e -> modifyInventory(false));
        saveInvBtn.addActionListener(e -> { saveInventory(); showInfo("Inventory saved."); });

        refreshInventoryModel();
        return p;
    }

    private void modifyInventory(boolean add) {
        String type = (String) inventoryTypeCombo.getSelectedItem();
        int units = (Integer) inventoryUnitsSpinner.getValue();
        if (units <= 0) { showError("Units must be > 0"); return; }
        int current = inventory.getOrDefault(type, 0);
        int updated = add ? current + units : current - units;
        if (updated < 0) { showError("Insufficient units to remove."); return; }
        inventory.put(type, updated);
        saveInventory();
        refreshInventoryModel();
        showInfo("Inventory updated.");
    }

    private void refreshInventoryModel() {
        inventoryModel.setRowCount(0);
        for (Map.Entry<String,Integer> e: inventory.entrySet()) {
            inventoryModel.addRow(new Object[]{e.getKey(), e.getValue()});
        }
    }

    // ---------- Requests Panel ----------
    private JPanel buildRequestsPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        p.setBorder(new EmptyBorder(8,8,8,8));

        requestModel = new DefaultTableModel(new Object[]{"ID","Requester","Blood","Units","Status"},0) {
            public boolean isCellEditable(int r,int c){return false;}
        };
        requestTable = new JTable(requestModel);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        reqNameField = new JTextField(12);
        reqBloodCombo = new JComboBox<>(BLOOD_TYPES);
        reqUnitsSpinner = new JSpinner(new SpinnerNumberModel(1,1,100,1));
        JButton addReqBtn = new JButton("Create Request");
        JButton fulfillBtn = new JButton("Fulfill Selected");
        JButton cancelBtn = new JButton("Cancel Selected");
        top.add(new JLabel("Requester:")); top.add(reqNameField);
        top.add(new JLabel("Blood:")); top.add(reqBloodCombo);
        top.add(new JLabel("Units:")); top.add(reqUnitsSpinner);
        top.add(addReqBtn); top.add(fulfillBtn); top.add(cancelBtn);

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(requestTable), BorderLayout.CENTER);

        addReqBtn.addActionListener(e -> createRequest());
        fulfillBtn.addActionListener(e -> fulfillSelectedRequest());
        cancelBtn.addActionListener(e -> cancelSelectedRequest());

        refreshRequestsModel();
        return p;
    }

    private void createRequest() {
        String requester = reqNameField.getText().trim();
        String blood = (String) reqBloodCombo.getSelectedItem();
        int units = (Integer) reqUnitsSpinner.getValue();
        if (requester.isEmpty()) { showError("Requester name required."); return; }
        String id = "R" + (requests.size() + 1) + "_" + System.currentTimeMillis()%10000;
        requests.add(new Request(id, requester, blood, units, "Pending"));
        saveRequests(); refreshRequestsModel();
        showInfo("Request created: " + id);
        reqNameField.setText("");
    }

    private void fulfillSelectedRequest() {
        int r = requestTable.getSelectedRow(); if (r == -1) { showError("Select a request."); return; }
        int mr = requestTable.convertRowIndexToModel(r);
        Request req = requests.get(mr);
        if (!"Pending".equals(req.status)) { showError("Request is not pending."); return; }
        int available = inventory.getOrDefault(req.bloodType, 0);
        if (available < req.units) { showError("Insufficient inventory ("+available+" units)."); return; }
        inventory.put(req.bloodType, available - req.units);
        req.status = "Fulfilled";
        saveInventory(); saveRequests();
        refreshInventoryModel(); refreshRequestsModel();
        showInfo("Request fulfilled.");
    }

    private void cancelSelectedRequest() {
        int r = requestTable.getSelectedRow(); if (r == -1) { showError("Select a request."); return; }
        int mr = requestTable.convertRowIndexToModel(r);
        Request req = requests.get(mr);
        if ("Fulfilled".equals(req.status)) { showError("Fulfilled request cannot be cancelled."); return; }
        req.status = "Cancelled";
        saveRequests(); refreshRequestsModel();
        showInfo("Request cancelled.");
    }

    private void refreshRequestsModel() {
        requestModel.setRowCount(0);
        for (Request rq: requests) {
            requestModel.addRow(new Object[]{rq.id, rq.requester, rq.bloodType, rq.units, rq.status});
        }
    }

    // ---------- Reports Panel ----------
    private JPanel buildReportsPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        p.setBorder(new EmptyBorder(8,8,8,8));
        JTextArea reportArea = new JTextArea();
        reportArea.setEditable(false);
        JScrollPane sp = new JScrollPane(reportArea);
        p.add(sp, BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        JButton refreshBtn = new JButton("Refresh Report");
        JButton saveAllBtn = new JButton("Save All Data");
        JButton exportDonorsCsv = new JButton("Export Donors CSV");
        top.add(refreshBtn); top.add(saveAllBtn); top.add(exportDonorsCsv);
        p.add(top, BorderLayout.NORTH);

        refreshBtn.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Blood Bank Report ===\n\n");
            sb.append("Inventory:\n");
            for (Map.Entry<String,Integer> en : inventory.entrySet()) sb.append(String.format("  %s : %d\n", en.getKey(), en.getValue()));
            sb.append("\nDonors (").append(donors.size()).append("):\n");
            for (Donor d : donors) sb.append(String.format("  %s (%s) - %s - %d yrs - last %s\n",
                    d.name, d.id, d.bloodType, d.age, d.lastDonation.format(DF)));
            sb.append("\nRequests:\n");
            for (Request r : requests) sb.append(String.format("  %s : %s (%d) -> %s\n", r.id, r.bloodType, r.units, r.status));
            reportArea.setText(sb.toString());
        });

        saveAllBtn.addActionListener(e -> { saveAllData(); showInfo("All data saved."); });
        exportDonorsCsv.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("donors_export.csv"));
            if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path out = fc.getSelectedFile().toPath();
                try {
                    List<String> lines = donors.stream().map(d -> String.join(",", d.id, d.name, d.bloodType, String.valueOf(d.age), d.contact, d.lastDonation.format(DF))).collect(Collectors.toList());
                    Files.write(out, lines, StandardCharsets.UTF_8);
                    showInfo("Exported donors to " + out.toString());
                } catch (Exception ex) { showError("Export failed: " + ex.getMessage()); }
            }
        });

        return p;
    }

    // ---------- Utilities ----------
    private void showError(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- Main ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(BloodBankApp::new);
    }
}
