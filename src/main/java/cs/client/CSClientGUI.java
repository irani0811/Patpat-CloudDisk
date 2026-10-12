package cs.client;

import cs.client.CSClient;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;


import javax.swing.table.TableCellRenderer;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.table.TableCellEditor;


/**
 * CS客户端图形界面 - 重新设计版本
 * 与index.html功能完全一致，界面更美观实用
 */
public class CSClientGUI extends JFrame {
    
    // 客户端连接
    private CSClient client;
    
    // 界面组件
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JTextArea authResultArea;
    
    // 文件管理组件
    private JTextField filePathField;
    private JCheckBox compressCheckBox;
    private JCheckBox encryptCheckBox;
    private JTextArea fileResultArea;
    private JTextArea uploadProgressArea;
    private JTextArea selectedFilesArea;
    private JTable fileListTable;
    private DefaultTableModel fileTableModel;
    private List<File> selectedFiles = new ArrayList<>();
    
    // 同步组件
    private JTextField syncPathField;
    private JTextField clientIdField;
    private JTextArea syncResultArea;
    private JCheckBox excludeSelfNotificationsCheckBox; 
    
    // 下载组件
    private JTextField fileIdField;
    private JTextField batchFileIdsField;
    private JTextField downloadThreadsField;
    private JComboBox<String> downloadFormatCombo;
    private JTextField linkExpireField;
    private JTextArea downloadResultArea;
    private JTable downloadFileTable;
    private DefaultTableModel downloadTableModel;

    // 共享管理组件
    private JTextField shareFileIdField;
    private JTextField shareWithField;
    private JComboBox<String> sharePermissionCombo;
    private JTextField shareExpireField;
    private JTable myShareTable;
    private JTable sharedWithMeTable;
    private DefaultTableModel myShareTableModel;
    private DefaultTableModel sharedWithMeTableModel;
    private JTextArea shareResultArea;


    
    // 日志区域
    private JTextArea logArea;
    private long lastNotificationId = 0L;  
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 文件数据缓存：按文件 ID 记录最新版本及其历史
    private final Map<Long, FileItem> fileCache = new LinkedHashMap<>();
    private final java.util.List<ShareItem> myShareItems = new ArrayList<>();
    private final java.util.List<ShareItem> sharedWithMeItems = new ArrayList<>();
    // 按钮动作枚举
    private enum ButtonAction {
        DOWNLOAD,
        HISTORY,
        DELETE,
        CANCEL_SHARE,
        DOWNLOAD_SHARED,
        UPLOAD_SHARED,
        DOWNLOAD_MY_SHARE,
        UPLOAD_MY_SHARE,
        HISTORY_SHARED,
        OPEN_CONFLICT
    }


    



    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    // =============== 数据结构 ===============
    private static class FileItem {
        long fileId;
        String filename;
        String path;
        long size;
        Long storedSize;
        LocalDateTime uploadedAt;
        List<VersionItem> versions = new ArrayList<>();
        }

    private static class VersionItem {
        long versionId;
        int versionNumber;
        boolean current;
        long size;
        String hash;
        String description;
        LocalDateTime createdAt;
    }

    // 共享数据结构
    private static class ShareItem {
        long shareId;
        long fileId;
        String filename;
        String shareWith;
        String permission;
        LocalDateTime createdAt;
        LocalDateTime expiresAt;
        boolean active;
    }

    private JDialog historyDialog;
    private JTable historyTable;
    private DefaultTableModel historyTableModel;
    private JTextArea historyDetailArea;
    private long historyDialogFileId;
    private String historyDialogFileName;
    
    public CSClientGUI() {
        // 设置系统编码
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("EasyCloudDisk - CS客户端");
        setSize(1200, 900);
        setLocationRelativeTo(null);
        
        // 设置中文字体
        setChineseFont();
        
        // 初始化客户端
        client = new CSClient();
    }
    
    /**
     * 初始化组件
     */
    private void initializeComponents() {
        // 认证组件
        usernameField = new JTextField(25);
        passwordField = new JPasswordField(25);
        loginButton = new JButton("登录");
        authResultArea = new JTextArea(4, 60);
        authResultArea.setEditable(false);
        authResultArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        
        // 文件管理组件
        filePathField = new JTextField(30);
        compressCheckBox = new JCheckBox("启用压缩");
        encryptCheckBox = new JCheckBox("启用加密");
        fileResultArea = new JTextArea(6, 60);
        fileResultArea.setEditable(false);
        fileResultArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        
        uploadProgressArea = new JTextArea(4, 60);
        uploadProgressArea.setEditable(false);
        uploadProgressArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        uploadProgressArea.setVisible(false);
        
        // 文件列表表格
        fileTableModel = new DefaultTableModel(new String[]{"文件名", "大小", "路径", "上传时间", "文件ID", "下载","删除", "历史版本"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5 || column == 6 || column == 7; // 下载与历史版本列可点击
            }
        };
        fileListTable = new JTable(fileTableModel);
        fileListTable.setRowHeight(25);
        fileListTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        fileListTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        fileListTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        fileListTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        fileListTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        fileListTable.getColumnModel().getColumn(5).setPreferredWidth(90);  // 下载
        fileListTable.getColumnModel().getColumn(6).setPreferredWidth(90);  // 删除
        fileListTable.getColumnModel().getColumn(7).setPreferredWidth(110); // 历史版本
        
        // 同步组件
        syncPathField = new JTextField(30);
        clientIdField = new JTextField(30);
        syncResultArea = new JTextArea(6, 60);
        syncResultArea.setEditable(false);
        syncResultArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        
        // 下载组件
        fileIdField = new JTextField(15);
        batchFileIdsField = new JTextField(30);
        downloadThreadsField = new JTextField(5);
        downloadFormatCombo = new JComboBox<>(new String[]{"ZIP压缩包", "TAR压缩包"});
        linkExpireField = new JTextField(5);
        downloadResultArea = new JTextArea(6, 60);
        downloadResultArea.setEditable(false);
        downloadResultArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        
        // 下载文件列表表格
        downloadTableModel = new DefaultTableModel(new String[]{"文件名", "大小", "路径", "上传时间", "文件ID", "操作"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5; // 只有操作列可编辑
            }
        };
        downloadFileTable = new JTable(downloadTableModel);
        downloadFileTable.setRowHeight(25);
        downloadFileTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        downloadFileTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        downloadFileTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        downloadFileTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        downloadFileTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        downloadFileTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        
        // 日志组件
        logArea = new JTextArea(8, 80);
        logArea.setEditable(false);
        logArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        
        // 设置默认值
        usernameField.setText("testuser");
        passwordField.setText("123456");
        filePathField.setText("/");
        syncPathField.setText("./sync_folder");
        clientIdField.setText("web_client_001");
        downloadThreadsField.setText("4");
        linkExpireField.setText("60");

        //历史版本
        historyTableModel = new DefaultTableModel(
            new String[]{"版本号", "大小", "哈希", "描述", "创建时间", "当前?", "下载", "回滚", "删除"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column >= 6;
            }
        };
        historyTable = new JTable(historyTableModel);
        historyTable.setRowHeight(24);
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(180);
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(5).setPreferredWidth(60);
        historyTable.getColumnModel().getColumn(6).setPreferredWidth(90);  // 下载
        historyTable.getColumnModel().getColumn(7).setPreferredWidth(90);  // 回滚
        historyTable.getColumnModel().getColumn(8).setPreferredWidth(90);  // 删除
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateHistoryDetail(historyTable.getSelectedRow());
            }
        });
        historyTable.getColumnModel().getColumn(6).setCellRenderer(new HistoryActionRenderer());
        historyTable.getColumnModel().getColumn(6).setCellEditor(new HistoryActionEditor(HistoryActionType.DOWNLOAD));
        historyTable.getColumnModel().getColumn(7).setCellRenderer(new HistoryActionRenderer());
        historyTable.getColumnModel().getColumn(7).setCellEditor(new HistoryActionEditor(HistoryActionType.ROLLBACK));
        historyTable.getColumnModel().getColumn(8).setCellRenderer(new HistoryActionRenderer());
        historyTable.getColumnModel().getColumn(8).setCellEditor(
                new HistoryActionEditor(HistoryActionType.DELETE));
        historyDetailArea = new JTextArea(5, 50);
        historyDetailArea.setEditable(false);
        historyDetailArea.setLineWrap(true);
        historyDetailArea.setWrapStyleWord(true);
        historyDetailArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        shareFileIdField = new JTextField(10);
        shareWithField = new JTextField(15);
        sharePermissionCombo = new JComboBox<>(new String[]{"READ", "WRITE", "ADMIN"});
        shareExpireField = new JTextField(5);
        shareResultArea = new JTextArea(6, 60);
        shareResultArea.setEditable(false);
        shareResultArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        myShareTableModel = new DefaultTableModel(
            new String[]{"共享ID", "文件ID", "文件名", "共享给", "权限", "过期时间", "状态",
                         "下载", "上传", "历史版本", "取消"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column >= 7;
            }
        };
        myShareTable = new JTable(myShareTableModel);
        myShareTable.setRowHeight(25);

        myShareTable.getColumnModel().getColumn(7)
            .setCellRenderer(new ButtonRenderer());
        myShareTable.getColumnModel().getColumn(7)
            .setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.DOWNLOAD_MY_SHARE));
        myShareTable.getColumnModel().getColumn(8)
            .setCellRenderer(new ButtonRenderer());
        myShareTable.getColumnModel().getColumn(8)
            .setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.UPLOAD_MY_SHARE));
        myShareTable.getColumnModel().getColumn(9)
            .setCellRenderer(new ButtonRenderer());
        myShareTable.getColumnModel().getColumn(9)
            .setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.HISTORY_SHARED));
        myShareTable.getColumnModel().getColumn(10)
            .setCellRenderer(new ButtonRenderer());
        myShareTable.getColumnModel().getColumn(10)
            .setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.CANCEL_SHARE));


        // --- “共享给我”表格初始化 ---
    sharedWithMeTableModel = new DefaultTableModel(
        new String[]{"共享ID", "文件ID", "文件名", "共享者", "权限", "过期时间", "状态",
                         "下载", "上传", "历史版本"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column >= 7;
        }
    };
    sharedWithMeTable = new JTable(sharedWithMeTableModel);
        sharedWithMeTable.setRowHeight(25);
        sharedWithMeTable.getColumnModel().getColumn(7)
            .setCellRenderer(new ButtonRenderer());
        sharedWithMeTable.getColumnModel().getColumn(7)
            .setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.DOWNLOAD_SHARED));
        sharedWithMeTable.getColumnModel().getColumn(8)
            .setCellRenderer(new ButtonRenderer());
        sharedWithMeTable.getColumnModel().getColumn(8)
            .setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.UPLOAD_SHARED));
        sharedWithMeTable.getColumnModel().getColumn(9)
            .setCellRenderer(new ButtonRenderer());
        sharedWithMeTable.getColumnModel().getColumn(9)
            .setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.HISTORY_SHARED));


    }
    
    /**
     * 设置布局
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // 创建选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("🔐 用户认证", createAuthPanel());
        tabbedPane.addTab("📁 文件管理", createFileManagementPanel());
        tabbedPane.addTab("🔄 同步功能", createSyncPanel());
        tabbedPane.addTab("⬇️ 文件下载", createDownloadPanel());
        tabbedPane.addTab("🤝 共享管理", createSharePanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // 添加日志面板
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder("📝 操作日志"));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(0, 200));
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        add(logPanel, BorderLayout.SOUTH);
    }
    
    /**
     * 创建认证面板
     */
    private JPanel createAuthPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("用户认证"));
        panel.setPreferredSize(new Dimension(800, 400));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        
        // 用户名
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("用户名:"), gbc);
        gbc.gridx = 1;
        panel.add(usernameField, gbc);
        
        // 密码
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("密码:"), gbc);
        gbc.gridx = 1;
        panel.add(passwordField, gbc);
        
        // 邮箱
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel emailLabel = new JLabel("邮箱:");
        panel.add(emailLabel, gbc);
        gbc.gridx = 1;
        JTextField emailField = new JTextField(25);
        emailField.setText("test@example.com");
        panel.add(emailField, gbc);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setPreferredSize(new Dimension(600, 50));
        
        JButton registerButton = new JButton("📝 注册");
        registerButton.setPreferredSize(new Dimension(100, 35));
        registerButton.addActionListener(e -> register(emailField.getText().trim()));
        buttonPanel.add(registerButton);
        
        loginButton = new JButton("🔑 登录");
        loginButton.setPreferredSize(new Dimension(100, 35));
        buttonPanel.add(loginButton);
        
        JButton logoutButton = new JButton("🚪 登出");
        logoutButton.setPreferredSize(new Dimension(100, 35));
        logoutButton.addActionListener(e -> logout());
        buttonPanel.add(logoutButton);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(buttonPanel, gbc);
        
        // 结果显示
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        JScrollPane authScrollPane = new JScrollPane(authResultArea);
        authScrollPane.setPreferredSize(new Dimension(600, 120));
        panel.add(authScrollPane, gbc);
        
        return panel;
    }
    
    /**
     * 创建文件管理面板
     */
    private JPanel createFileManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("文件管理"));
        
        // 顶部控制面板
        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // 选择文件
        gbc.gridx = 0; gbc.gridy = 0;
        controlPanel.add(new JLabel("选择文件:"), gbc);
        gbc.gridx = 1;
        JButton selectFileButton = new JButton("📁 选择文件");
        selectFileButton.setPreferredSize(new Dimension(120, 35));
        selectFileButton.addActionListener(e -> selectFiles());
        controlPanel.add(selectFileButton, gbc);
        
        // 文件路径
        gbc.gridx = 0; gbc.gridy = 1;
        controlPanel.add(new JLabel("文件路径:"), gbc);
        gbc.gridx = 1;
        controlPanel.add(filePathField, gbc);
        
        // 选项
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.add(compressCheckBox);
        optionsPanel.add(encryptCheckBox);
        controlPanel.add(optionsPanel, gbc);
        
        // 按钮面板
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton uploadButton = new JButton("⬆️ 智能上传");
        uploadButton.setPreferredSize(new Dimension(120, 35));
        uploadButton.addActionListener(e -> uploadFiles());
        buttonPanel.add(uploadButton);
        
        JButton listButton = new JButton("📋 获取文件列表");
        listButton.setPreferredSize(new Dimension(120, 35));
        listButton.addActionListener(e -> getFileList());
        buttonPanel.add(listButton);
        
        JButton createDirButton = new JButton("📁 创建目录");
        createDirButton.setPreferredSize(new Dimension(120, 35));
        createDirButton.addActionListener(e -> createDirectory());
        buttonPanel.add(createDirButton);
        
        JButton progressButton = new JButton("📊 查看上传进度");
        progressButton.setPreferredSize(new Dimension(120, 35));
        progressButton.addActionListener(e -> getUploadProgress());
        buttonPanel.add(progressButton);
        
        controlPanel.add(buttonPanel, gbc);
        
        // 文件列表表格
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(new TitledBorder("📋 文件列表"));
        JScrollPane tableScrollPane = new JScrollPane(fileListTable);
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        
        fileListTable.getColumn("下载").setCellRenderer(new ButtonRenderer());
        fileListTable.getColumn("下载").setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.DOWNLOAD));

        fileListTable.getColumn("删除").setCellRenderer(new ButtonRenderer());
        fileListTable.getColumn("删除").setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.DELETE));

        fileListTable.getColumn("历史版本").setCellRenderer(new ButtonRenderer());
        fileListTable.getColumn("历史版本").setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.HISTORY));
        JButton refreshButton = new JButton("🔄 刷新列表");
        refreshButton.addActionListener(e -> getFileList());
        tablePanel.add(refreshButton, BorderLayout.SOUTH);
        
        // 结果显示区域
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(new TitledBorder("📄 操作结果"));
        
        JTabbedPane resultTabbedPane = new JTabbedPane();
        
        JScrollPane resultScrollPane = new JScrollPane(fileResultArea);
        resultTabbedPane.addTab("操作结果", resultScrollPane);
        
        selectedFilesArea = new JTextArea(6, 60);
        selectedFilesArea.setEditable(false);
        selectedFilesArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        selectedFilesArea.setBackground(new Color(248, 249, 250));
        JScrollPane selectedFilesScrollPane = new JScrollPane(selectedFilesArea);
        resultTabbedPane.addTab("选择的文件", selectedFilesScrollPane);
        
        JScrollPane progressScrollPane = new JScrollPane(uploadProgressArea);
        progressScrollPane.setVisible(false);
        resultTabbedPane.addTab("上传进度", progressScrollPane);
        
        resultPanel.add(resultTabbedPane, BorderLayout.CENTER);
        
        JScrollPane controlScrollPane = new JScrollPane(controlPanel);
        controlScrollPane.setBorder(null);
        controlScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        JSplitPane tableResultSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, resultPanel);
        tableResultSplit.setResizeWeight(0.65);
        tableResultSplit.setOneTouchExpandable(true);
        
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, controlScrollPane, tableResultSplit);
        mainSplit.setResizeWeight(0.35);
        mainSplit.setOneTouchExpandable(true);
        
        panel.add(mainSplit, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建同步面板
     */
    private JPanel createSyncPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("同步功能"));
        panel.setPreferredSize(new Dimension(800, 400));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        
        // 使用说明
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("💡 使用说明: 选择本地文件夹后，系统会自动监控文件夹变化并与云端保持同步。支持多设备同步和团队协作。");
        infoLabel.setForeground(new Color(0, 123, 255));
        infoLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        panel.add(infoLabel, gbc);
        
        // 同步路径
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("同步路径:"), gbc);
        gbc.gridx = 1;
        
        // 创建路径选择面板
        JPanel pathPanel = new JPanel(new BorderLayout());
        pathPanel.add(syncPathField, BorderLayout.CENTER);
        
        JButton selectPathButton = new JButton("📁 选择路径");
        selectPathButton.setPreferredSize(new Dimension(100, 30));
        selectPathButton.addActionListener(e -> selectSyncPath());
        pathPanel.add(selectPathButton, BorderLayout.EAST);
        
        panel.add(pathPanel, gbc);
        
        // 客户端ID
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("客户端ID:"), gbc);
        gbc.gridx = 1;
        panel.add(clientIdField, gbc);
        
        // 按钮面板
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton startSyncButton = new JButton("▶️ 开始同步");
        startSyncButton.setPreferredSize(new Dimension(120, 35));
        startSyncButton.addActionListener(e -> startSync());
        buttonPanel.add(startSyncButton);
        
        JButton stopSyncButton = new JButton("⏹️ 停止同步");
        stopSyncButton.setPreferredSize(new Dimension(120, 35));
        stopSyncButton.addActionListener(e -> stopSync());
        buttonPanel.add(stopSyncButton);
        
        JButton eventsButton = new JButton("📋 获取同步事件");
        eventsButton.setPreferredSize(new Dimension(120, 35));
        eventsButton.addActionListener(e -> getSyncEvents());
        buttonPanel.add(eventsButton);

        JButton notificationsButton = new JButton("🔔 云端通知");
        notificationsButton.setPreferredSize(new Dimension(120, 35));
        notificationsButton.addActionListener(e -> getCloudNotifications());
        buttonPanel.add(notificationsButton);

        excludeSelfNotificationsCheckBox = new JCheckBox("排除自身事件");
        excludeSelfNotificationsCheckBox.setToolTipText("勾选后不显示本客户端触发的事件");
        buttonPanel.add(excludeSelfNotificationsCheckBox);
        
        panel.add(buttonPanel, gbc);
        
        // 结果显示
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        JScrollPane syncScrollPane = new JScrollPane(syncResultArea);
        syncScrollPane.setPreferredSize(new Dimension(600, 150));
        panel.add(syncScrollPane, gbc);
        
        return panel;
    }
    
    /**
     * 创建下载面板
     */
    private JPanel createDownloadPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("文件下载"));
        
        // 顶部控制面板
        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // 文件ID
        gbc.gridx = 0; gbc.gridy = 0;
        controlPanel.add(new JLabel("文件ID:"), gbc);
        gbc.gridx = 1;
        controlPanel.add(fileIdField, gbc);
        
        // 批量文件ID
        gbc.gridx = 0; gbc.gridy = 1;
        controlPanel.add(new JLabel("批量文件ID:"), gbc);
        gbc.gridx = 1;
        controlPanel.add(batchFileIdsField, gbc);
        
        // 下载线程数
        gbc.gridx = 0; gbc.gridy = 2;
        controlPanel.add(new JLabel("下载线程数:"), gbc);
        gbc.gridx = 1;
        controlPanel.add(downloadThreadsField, gbc);
        
        // 下载格式
        gbc.gridx = 0; gbc.gridy = 3;
        controlPanel.add(new JLabel("下载格式:"), gbc);
        gbc.gridx = 1;
        controlPanel.add(downloadFormatCombo, gbc);
        
        // 链接有效期
        gbc.gridx = 0; gbc.gridy = 4;
        controlPanel.add(new JLabel("链接有效期(分钟):"), gbc);
        gbc.gridx = 1;
        controlPanel.add(linkExpireField, gbc);
        
        // 按钮面板
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton singleDownloadButton = new JButton("⬇️ 单文件下载");
        singleDownloadButton.setPreferredSize(new Dimension(120, 35));
        singleDownloadButton.addActionListener(e -> downloadSingleFile());
        buttonPanel.add(singleDownloadButton);
        
        JButton multiThreadButton = new JButton("⚡ 多线程下载");
        multiThreadButton.setPreferredSize(new Dimension(120, 35));
        multiThreadButton.addActionListener(e -> downloadMultiThread());
        buttonPanel.add(multiThreadButton);
        
        JButton batchDownloadButton = new JButton("📦 批量下载");
        batchDownloadButton.setPreferredSize(new Dimension(120, 35));
        batchDownloadButton.addActionListener(e -> downloadBatchFiles());
        buttonPanel.add(batchDownloadButton);
        
        JButton generateLinkButton = new JButton("🔗 生成下载链接");
        generateLinkButton.setPreferredSize(new Dimension(120, 35));
        generateLinkButton.addActionListener(e -> generateDownloadLink());
        buttonPanel.add(generateLinkButton);
        
        JButton getMyFilesButton = new JButton("📁 获取我的文件");
        getMyFilesButton.setPreferredSize(new Dimension(120, 35));
        getMyFilesButton.setBackground(new Color(40, 167, 69));
        getMyFilesButton.setForeground(Color.WHITE);
        getMyFilesButton.addActionListener(e -> getMyFiles());
        buttonPanel.add(getMyFilesButton);
        
        controlPanel.add(buttonPanel, gbc);
        
        panel.add(controlPanel, BorderLayout.NORTH);
        
        // 文件列表表格
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(new TitledBorder("📋 我的文件"));
        JScrollPane tableScrollPane = new JScrollPane(downloadFileTable);
        tableScrollPane.setPreferredSize(new Dimension(0, 300));
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        
        // 设置表格按钮
        downloadFileTable.getColumn("操作").setCellRenderer(new ButtonRenderer());
        downloadFileTable.getColumn("操作").setCellEditor(new ButtonEditor(new JCheckBox()));
    
        // 添加操作按钮到表格
        JButton refreshButton = new JButton("🔄 刷新列表");
        refreshButton.addActionListener(e -> getMyFiles());
        tablePanel.add(refreshButton, BorderLayout.SOUTH);
        
        panel.add(tablePanel, BorderLayout.CENTER);
        
        // 结果显示区域
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(new TitledBorder("📄 操作结果"));
        JScrollPane resultScrollPane = new JScrollPane(downloadResultArea);
        resultScrollPane.setPreferredSize(new Dimension(0, 100));
        resultPanel.add(resultScrollPane, BorderLayout.CENTER);
        
        panel.add(resultPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    //共享面板
        private JPanel createSharePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("共享管理"));

        // 上方表单
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("文件ID:"), gbc);
        gbc.gridx = 1;
        formPanel.add(shareFileIdField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("共享给(用户名):"), gbc);
        gbc.gridx = 1;
        formPanel.add(shareWithField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("权限:"), gbc);
        gbc.gridx = 1;
        formPanel.add(sharePermissionCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("过期天数(可选):"), gbc);
        gbc.gridx = 1;
        formPanel.add(shareExpireField, gbc);

        JButton createShareButton = new JButton("创建共享");
        createShareButton.addActionListener(e -> createShare());
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        formPanel.add(createShareButton, gbc);

        // 中间两个表格
        JPanel mySharePanel = new JPanel(new BorderLayout());
        mySharePanel.setBorder(new TitledBorder("我共享的文件"));
        JScrollPane myShareScroll = new JScrollPane(myShareTable);
        mySharePanel.add(myShareScroll, BorderLayout.CENTER);
        JButton refreshMyShareBtn = new JButton("刷新");
        refreshMyShareBtn.addActionListener(e -> loadMyShares(true));
        mySharePanel.add(refreshMyShareBtn, BorderLayout.SOUTH);

        JPanel sharedPanel = new JPanel(new BorderLayout());
        sharedPanel.setBorder(new TitledBorder("共享给我的文件"));
        JScrollPane sharedScroll = new JScrollPane(sharedWithMeTable);
        sharedPanel.add(sharedScroll, BorderLayout.CENTER);
        JButton refreshSharedBtn = new JButton("刷新");
        refreshSharedBtn.addActionListener(e -> loadSharedWithMe(true));
        sharedPanel.add(refreshSharedBtn, BorderLayout.SOUTH);

        JSplitPane tablesSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mySharePanel, sharedPanel);
        tablesSplit.setResizeWeight(0.5);
        tablesSplit.setOneTouchExpandable(true);
        tablesSplit.setContinuousLayout(true);

        // 底部结果
        JScrollPane resultScroll = new JScrollPane(shareResultArea);
        resultScroll.setBorder(new TitledBorder("共享操作结果"));
        resultScroll.setPreferredSize(new Dimension(0, 150));

        JScrollPane formScroll = new JScrollPane(formPanel);
        formScroll.setBorder(null);
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane tablesResultSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablesSplit, resultScroll);
        tablesResultSplit.setResizeWeight(0.65);
        tablesResultSplit.setOneTouchExpandable(true);
        tablesResultSplit.setContinuousLayout(true);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formScroll, tablesResultSplit);
        mainSplit.setResizeWeight(0.3);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setContinuousLayout(true);

        panel.add(mainSplit, BorderLayout.CENTER);

        // 设置表格按钮
        myShareTable.getColumnModel().getColumn(7)
            .setCellRenderer(new ButtonRenderer());
        myShareTable.getColumnModel().getColumn(7)
            .setCellEditor(new ButtonEditor(new JCheckBox(), ButtonAction.CANCEL_SHARE));

        return panel;
    }
    

    private void createShare() {
    if (!client.isLoggedIn()) {
        JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.INFORMATION_MESSAGE);
        return;
    }
    try {
        long fileId = Long.parseLong(shareFileIdField.getText().trim());
        String shareWith = shareWithField.getText().trim();
        if (shareWith.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入共享用户", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String permission = (String) sharePermissionCombo.getSelectedItem();
        Integer expireDays = null;
        if (!shareExpireField.getText().trim().isEmpty()) {
            expireDays = Integer.parseInt(shareExpireField.getText().trim());
        }
        String response = createShareAPI(fileId, shareWith, permission, expireDays);
        shareResultArea.append("[创建共享] " + response + "\n");
        logMessage("创建共享响应: " + response);
        populateShareTables();
    } catch (NumberFormatException nfe) {
        JOptionPane.showMessageDialog(this, "文件ID或过期天数格式不正确", "错误", JOptionPane.ERROR_MESSAGE);
    }
}

    private void populateShareTables() {
    loadMyShares(false);
    loadSharedWithMe(false);
}

    private void loadMyShares(boolean showMessage) {
        if (!client.isLoggedIn()) return;
        SwingUtilities.invokeLater(() -> {
            String response = getMySharesAPI();
            updateShareTable(response, myShareTableModel, true);
            if (showMessage) {
                JOptionPane.showMessageDialog(this, "已刷新我的共享", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    private void loadSharedWithMe(boolean showMessage) {
        if (!client.isLoggedIn()) return;
        SwingUtilities.invokeLater(() -> {
            String response = getSharedWithMeAPI();
            updateShareTable(response, sharedWithMeTableModel, false);
            if (showMessage) {
                JOptionPane.showMessageDialog(this, "已刷新共享给我的文件", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

// --- 共享表刷新 ---
private void updateShareTable(String response, DefaultTableModel model, boolean ownsShare) {
    model.setRowCount(0);
    if (ownsShare) {
        myShareItems.clear();
    } else if (model == sharedWithMeTableModel) {
        sharedWithMeItems.clear();
    }
    if (response == null || response.isEmpty() || !response.contains("\"success\":true")) {
        if (response != null) {
            shareResultArea.append(response + "\n");
        }
        return;
    }

    int sharesIdx = response.indexOf("\"shares\"");
    if (sharesIdx == -1) return;

    int arrayStart = response.indexOf('[', sharesIdx);
    int arrayEnd = response.indexOf(']', arrayStart);
    if (arrayStart == -1 || arrayEnd == -1) return;

    String sharesArray = response.substring(arrayStart + 1, arrayEnd);
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{[^{}]*\\}");
    java.util.regex.Matcher matcher = pattern.matcher(sharesArray);
    while (matcher.find()) {
        ShareItem item = parseShareItem(matcher.group(), ownsShare);
        if (item == null) continue;

        if (ownsShare) {
            myShareItems.add(item);
            model.addRow(new Object[]{
                item.shareId, item.fileId, item.filename, item.shareWith,
                item.permission, item.expiresAt != null ? item.expiresAt.toString() : "-",
                item.active ? "有效" : "无效",
                item.active ? "下载" : "-",
                item.active ? "上传" : "-",
                "历史版本",
                "取消"
            });
        } else {
            ensureSharedFileCache(item);
            sharedWithMeItems.add(item);
            model.addRow(new Object[]{
                item.shareId, item.fileId, item.filename, item.shareWith,
                item.permission, item.expiresAt != null ? item.expiresAt.toString() : "-",
                item.active ? "有效" : "无效",
                item.active ? "下载" : "-",
                item.active ? "上传" : "-",
                "历史版本"
            });
        }
    }
}

// --- 下载入口统一 ---
private static class DownloadResult {
    final boolean success;
    final String filename;
    final byte[] data;
    final String message;
    final String metadataJson;

    DownloadResult(boolean success, String filename, byte[] data,
                   String message, String metadataJson) {
        this.success = success;
        this.filename = filename;
        this.data = data;
        this.message = message;
        this.metadataJson = metadataJson;
    }
}

private DownloadResult executeDownloadRequest(long fileId) throws Exception {
    java.net.URL url = new java.net.URL("http://localhost:8080/api/files/download/" + fileId);
    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + client.getToken());

    int responseCode = conn.getResponseCode();
    if (responseCode != 200) {
        return new DownloadResult(false, null, null,
            "HTTP " + responseCode + " " + readErrorResponse(conn), null);
    }

    String disposition = conn.getHeaderField("Content-Disposition");
    String filename = (disposition != null && disposition.contains("filename="))
        ? disposition.substring(disposition.indexOf("filename=") + 9).replace("\"", "")
        : ("download_" + fileId);

    byte[] fileBytes;
    try (java.io.InputStream is = conn.getInputStream();
         java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        fileBytes = baos.toByteArray();
    }

    String metaJson = conn.getHeaderField("X-File-Metadata");
    return new DownloadResult(true, filename, fileBytes, "OK", metaJson);
}

private void downloadFileToPath(long fileId, String savePath) {
    SwingUtilities.invokeLater(() -> {
        try {
            DownloadResult result = executeDownloadRequest(fileId);
            if (!result.success) {
                downloadResultArea.setText("下载失败: " + result.message);
                logMessage("下载失败: " + result.message);
                return;
            }

            File target = new File(savePath, result.filename);
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(result.data);
            }

            downloadResultArea.setText("文件下载成功: " + target.getAbsolutePath());
            downloadResultArea.setBackground(new Color(212, 237, 218));
            logMessage("文件下载成功: " + target.getAbsolutePath());

            refreshCachesAfterDownload(fileId, result.metadataJson);
        } catch (Exception e) {
            downloadResultArea.setText("下载失败: " + e.getMessage());
            logMessage("下载失败: " + e.getMessage());
        }
    });
}

// 兼容原 Long 调用
private void downloadFileToPath(Long fileId, String savePath) {
    downloadFileToPath(fileId.longValue(), savePath);
}


// --- 下载后刷新缓存 ---
private void refreshCachesAfterDownload(long fileId, String metadataJson) {
    if (metadataJson == null || metadataJson.isEmpty()) {
        loadSharedWithMe(false);
        return;
    }

    try {
        FileItem cacheItem = fileCache.computeIfAbsent(fileId, id -> new FileItem());
        cacheItem.fileId = fileId;

        String filename = extractValue(metadataJson, "filename");
        if (filename != null) cacheItem.filename = filename;

        String path = extractValue(metadataJson, "filePath");
        if (path != null) cacheItem.path = path;

        String size = extractValue(metadataJson, "fileSize");
        if (size != null) cacheItem.size = Long.parseLong(size);

        String updated = extractValue(metadataJson, "updatedAt");
        if (updated != null && !updated.isEmpty()) {
            cacheItem.uploadedAt = parseDateTimeSafely(updated);
        }

        loadHistoryVersions(fileId, false);
        loadSharedWithMe(false);
    } catch (Exception e) {
        logMessage("刷新共享缓存失败: " + e.getMessage());
    }
}


// --- 共享文件上传/冲突处理 ---
private void handleSharedUpload(ShareItem item) {
    if (!client.isLoggedIn()) {
        JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("选择要上传的文件");
    if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
    }
    File selectedFile = fileChooser.getSelectedFile();
    if (selectedFile == null || !selectedFile.isFile()) {
        JOptionPane.showMessageDialog(this, "请选择有效的文件", "提示", JOptionPane.WARNING_MESSAGE);
        return;
    }

    try {
        byte[] fileBytes = java.nio.file.Files.readAllBytes(selectedFile.toPath());
        String base64Content = Base64.getEncoder().encodeToString(fileBytes);
        String clientHash = calculateFileHash(selectedFile);
        Integer clientVersion = null;
        FileItem cachedItem = fileCache.get(item.fileId);
        if (cachedItem != null) {
            clientVersion = cachedItem.versions.stream()
                .filter(v -> v.current)
                .map(v -> v.versionNumber)
                .findFirst()
                .orElse(null);
        }

        String conflictResp = checkUploadConflictAPI(item.fileId, clientVersion, clientHash);
        if (conflictResp == null || conflictResp.startsWith("ERROR::")) {
            shareResultArea.append("[共享上传] 冲突检测失败: " + conflictResp + "\n");
            return;
        }
        if (!conflictResp.contains("\"success\":true")) {
            shareResultArea.append("[共享上传] 冲突检测返回: " + conflictResp + "\n");
            return;
        }

        boolean conflict = hasUploadConflict(conflictResp);
        String strategy = "overwrite";
        if (conflict) {
            String latestVersion = extractLatestInfo(conflictResp, "latestVersion");
            String latestHash = extractLatestInfo(conflictResp, "latestHash");
            String owner = extractLatestInfo(conflictResp, "owner");
            Object[] options = {"覆盖上传", "保存副本", "取消"};
            int choice = JOptionPane.showOptionDialog(
                this,
                "检测到冲突：\n" +
                "最新版本: " + (latestVersion != null ? latestVersion : "-") + "\n" +
                "服务器哈希: " + (latestHash != null ? latestHash : "-") + "\n" +
                "拥有者: " + (owner != null ? owner : "-") + "\n\n" +
                "请选择处理方式：",
                "共享文件冲突",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]
            );
            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                shareResultArea.append("[共享上传] 用户取消上传\n");
                return;
            }
            strategy = (choice == 0) ? "overwrite" : "duplicate";
        }

        String note = "协作更新，由 " + usernameField.getText().trim()
                + " 于 " + java.time.LocalDateTime.now() + " 提交";
        String resolveResp = resolveConflictAPI(
            item.fileId,
            base64Content,
            strategy,
            note,
            compressCheckBox.isSelected(),
            encryptCheckBox.isSelected()
        );

        if (resolveResp != null && resolveResp.contains("\"success\":true")) {
            shareResultArea.append("[共享上传] 上传成功\n");
            String conflictName = extractLatestInfo(resolveResp, "conflictFilename");
            if (conflictName != null) {
                shareResultArea.append("[共享上传] 本地命名提示: " + conflictName + "\n");
            }
            logMessage("共享上传响应: " + resolveResp);
            getFileList();
            loadSharedWithMe(false);
        } else {
            shareResultArea.append("[共享上传] 上传失败: " + resolveResp + "\n");
            logMessage("共享上传失败: " + resolveResp);
        }
    } catch (Exception ex) {
        shareResultArea.append("[共享上传] 发生错误: " + ex.getMessage() + "\n");
        logMessage("共享上传异常: " + ex.getMessage());
    }
}


       private ShareItem parseShareItem(String json, boolean ownsShare) {
        ShareItem item = new ShareItem();
        try {
            String id = extractValue(json, "shareId");
            String fileId = extractValue(json, "fileId");
            item.shareId = id != null ? Long.parseLong(id) : -1;
            item.fileId = fileId != null ? Long.parseLong(fileId) : -1;
            item.permission = extractValue(json, "permission");
            String createdAt = extractValue(json, "createdAt");
            String expiresAt = extractValue(json, "expiresAt");
            item.createdAt = createdAt != null && !createdAt.isEmpty() ? LocalDateTime.parse(createdAt) : null;
            item.expiresAt = expiresAt != null && !expiresAt.isEmpty() ? LocalDateTime.parse(expiresAt) : null;
            String active = extractValue(json, "isActive");
            if (active == null) {
                active = extractValue(json, "active");
            }
            item.active = active == null || Boolean.parseBoolean(active);
            item.filename = extractValue(json, "file", "filename");
            if (item.filename == null) {
                item.filename = extractValue(json, "filename");
            }
            item.shareWith = extractValue(json, ownsShare ? "shareTo" : "shareFrom");
            return item;
        } catch (Exception e) {
            shareResultArea.append("解析共享数据失败: " + e.getMessage() + "\n");
            return null;
        }
    }

    private void cancelShare(long shareId) {
        if (!client.isLoggedIn()) return;
        String response = cancelShareAPI(shareId);
        shareResultArea.append("[取消共享] " + response + "\n");
        logMessage("取消共享响应: " + response);
        populateShareTables();
    }
    
    private void ensureSharedFileCache(ShareItem item) {
        if (item == null || item.fileId <= 0) {
            return;
        }
        fileCache.compute(item.fileId, (id, existing) -> {
            if (existing == null) {
                FileItem seeded = new FileItem();
                seeded.fileId = id;
                seeded.filename = item.filename != null ? item.filename : ("共享文件-" + id);
                seeded.path = item.filename != null ? "共享文件" : "";
                seeded.size = 0L;
                seeded.versions = new ArrayList<>();
                return seeded;
            }
            if ((existing.filename == null || existing.filename.isEmpty() || "-".equals(existing.filename))
                    && item.filename != null) {
                existing.filename = item.filename;
            }
            return existing;
        });
    }
    
    /**
     * 设置事件处理器
     */
    private void setupEventHandlers() {
        loginButton.addActionListener(e -> login());
        

       
        // 为下载文件表格添加操作按钮
        downloadFileTable.getColumn("操作").setCellRenderer(new ButtonRenderer());
        downloadFileTable.getColumn("操作").setCellEditor(new ButtonEditor(new JCheckBox()));
    }
    
    /**
     * 设置中文字体
     */
    private void setChineseFont() {
        try {
            // 获取系统默认字体
            Font defaultFont = UIManager.getFont("Label.font");
            if (defaultFont == null) {
                defaultFont = new Font("Dialog", Font.PLAIN, 12);
            }
            
            // 尝试使用系统中文字体
            Font chineseFont = null;
            String[] fontNames = {"Microsoft YaHei", "SimSun", "宋体", "黑体", "Dialog"};
            
            for (String fontName : fontNames) {
                try {
                    chineseFont = new Font(fontName, Font.PLAIN, 12);
                    if (chineseFont.canDisplay('中') && chineseFont.canDisplay('文')) {
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            
            if (chineseFont == null) {
                chineseFont = defaultFont;
            }
            
            // 设置所有UI组件的字体
            UIManager.put("Button.font", chineseFont);
            UIManager.put("Label.font", chineseFont);
            UIManager.put("TextField.font", chineseFont);
            UIManager.put("PasswordField.font", chineseFont);
            UIManager.put("TextArea.font", chineseFont);
            UIManager.put("Table.font", chineseFont);
            UIManager.put("TableHeader.font", chineseFont);
            UIManager.put("TabbedPane.font", chineseFont);
            UIManager.put("TitledBorder.font", chineseFont);
            UIManager.put("CheckBox.font", chineseFont);
            UIManager.put("ComboBox.font", chineseFont);
            UIManager.put("ScrollPane.font", chineseFont);
            UIManager.put("Panel.font", chineseFont);
            
            // 强制更新所有组件
            SwingUtilities.updateComponentTreeUI(this);
            
            System.out.println("使用字体: " + chineseFont.getName());
            
        } catch (Exception e) {
            System.err.println("设置中文字体失败: " + e.getMessage());
        }
    }
    
    // ==================== 认证功能 ====================
    
    /**
     * 用户注册
     */
    private void register(String email) {
        SwingUtilities.invokeLater(() -> {
            try {
                String response = registerUser(usernameField.getText().trim(), 
                    new String(passwordField.getPassword()), email);
                authResultArea.setText(response);
                logMessage("用户注册: " + response);
            } catch (Exception e) {
                authResultArea.setText("注册失败: " + e.getMessage());
                logMessage("注册失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 用户登录
     */
    private void login() {
        SwingUtilities.invokeLater(() -> {
            try {
                boolean success = client.connect();
                if (!success) {
                    authResultArea.setText("连接服务器失败");
                    logMessage("连接服务器失败");
                    return;
                }
                
                success = client.login(usernameField.getText().trim(), 
                    new String(passwordField.getPassword()));
                
                if (success) {
                    authResultArea.setText("登录成功");
                    authResultArea.setBackground(new Color(212, 237, 218));
                    logMessage("登录成功");

                    populateShareTables();
                } else {
                    authResultArea.setText("登录失败");
                    authResultArea.setBackground(new Color(248, 215, 218));
                    logMessage("登录失败");
                }
            } catch (Exception e) {
                authResultArea.setText("登录失败: " + e.getMessage());
                logMessage("登录失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 用户登出
     */
    private void logout() {
        SwingUtilities.invokeLater(() -> {
            try {
                boolean success = client.logout();
                if (success) {
                    authResultArea.setText("登出成功");
                    authResultArea.setBackground(new Color(248, 249, 250));
                    logMessage("登出成功");
                } else {
                    authResultArea.setText("登出失败");
                    logMessage("登出失败");
                }
            } catch (Exception e) {
                authResultArea.setText("登出失败: " + e.getMessage());
                logMessage("登出失败: " + e.getMessage());
            }
        });
    }
    
    // ==================== 文件管理功能 ====================
    
    /**
     * 选择文件
     */
    private void selectFiles() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setDialogTitle("选择要上传的文件");
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFiles.clear();
            for (File file : fileChooser.getSelectedFiles()) {
                selectedFiles.add(file);
            }
            logMessage("已选择 " + selectedFiles.size() + " 个文件");
            
            // 更新选择的文件显示
            updateSelectedFilesDisplay();
        }
    }
    
    /**
     * 更新选择的文件显示
     */
    private void updateSelectedFilesDisplay() {
        if (selectedFilesArea != null) {
            StringBuilder fileList = new StringBuilder();
            fileList.append("已选择的文件 (").append(selectedFiles.size()).append(" 个):\n\n");
            
            for (int i = 0; i < selectedFiles.size(); i++) {
                File file = selectedFiles.get(i);
                fileList.append("[").append(i + 1).append("] ").append(file.getName())
                       .append(" (").append(formatFileSize(file.length())).append(")\n")
                       .append("    路径: ").append(file.getAbsolutePath()).append("\n\n");
            }
            
            selectedFilesArea.setText(fileList.toString());
        }
    }
    
    
    
    /**
     * 选择同步路径
     */
    private void selectSyncPath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("选择同步文件夹");
        fileChooser.setApproveButtonText("选择文件夹");
        
        // 设置默认路径为当前用户目录
        String defaultPath = System.getProperty("user.home");
        File currentDir = new File(defaultPath);
        fileChooser.setCurrentDirectory(currentDir);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            if (selectedDir != null && selectedDir.isDirectory()) {
                // 确保获取实际选择的文件夹路径
                String selectedPath = selectedDir.getAbsolutePath();
                String normalizedPath = selectedDir.toPath().normalize().toString();
                
                // 如果用户选择了"D:\"这样的根目录，提示用户选择具体的同步文件夹
                if (selectedDir.getParentFile() == null || selectedPath.matches("^[A-Za-z]:[\\\\/]?$")) {
                    JOptionPane.showMessageDialog(this,
                        "请选择一个具体的同步文件夹，而不是磁盘根目录！",
                        "选择错误",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                syncPathField.setText(normalizedPath);
                logMessage("已选择同步路径: " + normalizedPath);
                
                // 检查文件夹是否可写
                if (!selectedDir.canWrite()) {
                    JOptionPane.showMessageDialog(this, 
                        "警告: 所选文件夹没有写入权限，可能影响同步功能！", 
                        "权限警告", 
                        JOptionPane.WARNING_MESSAGE);
                }
            }
        }
    }


    private boolean hasUploadConflict(String response) {
    return response != null && response.contains("\"conflict\":true");
}

private String extractLatestInfo(String response, String key) {
    return extractValue(response, key);
}
    /**
     * 智能上传文件
     */
   private void uploadFiles() {
    if (selectedFiles.isEmpty()) {
        JOptionPane.showMessageDialog(this, "请先选择文件", "提示", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    SwingUtilities.invokeLater(() -> {
        try {
            uploadProgressArea.setVisible(true);
            uploadProgressArea.setText("开始上传文件...\n");

            int successCount = 0;
            int failCount = 0;

            for (File file : selectedFiles) {
                try {
                    long originalSize = file.length();
                    uploadProgressArea.append(
            "正在上传: " + file.getName() +
            " (本地大小: " + formatFileSize(originalSize) + ")\n"
        );
                    uploadProgressArea.append("正在检查: " + file.getName() + "\n");

                    byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                    String base64Content = Base64.getEncoder().encodeToString(fileBytes);
                    String targetPath = filePathField.getText();
                    boolean handledByConflictAPI = false;

                    Optional<FileItem> cached = findCachedFileByNameAndPath(file.getName(), targetPath);
                    if (cached.isPresent()) {
                        FileItem item = cached.get();
                        String clientHash = calculateFileHash(file);
                        Integer clientVersion = item.versions.stream()
                                .filter(v -> v.current)
                                .map(v -> v.versionNumber)
                                .findFirst()
                                .orElse(item.versions.isEmpty() ? null : item.versions.get(0).versionNumber);

                        String conflictResp = checkUploadConflictAPI(item.fileId, clientVersion, clientHash);
                        if (hasUploadConflict(conflictResp)) {
                            String latestVersion = extractLatestInfo(conflictResp, "latestVersion");
                            String latestHash = extractLatestInfo(conflictResp, "latestHash");
                            String owner = extractLatestInfo(conflictResp, "owner");

                            Object[] options = {"覆盖上传", "保存副本", "取消"};
                            int choice = JOptionPane.showOptionDialog(
                                this,
                                "检测到冲突：\n" +
                                    "最新版本: " + (latestVersion != null ? latestVersion : "-") + "\n" +
                                    "服务器哈希: " + (latestHash != null ? latestHash : "-") + "\n" +
                                    "拥有者: " + (owner != null ? owner : "-") + "\n\n" +
                                    "请选择处理方式：",
                                "上传冲突",
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.WARNING_MESSAGE,
                                null,
                                options,
                                options[0]
                            );

                            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                                uploadProgressArea.append("⚠️ 已取消上传 " + file.getName() + "\n");
                                continue;
                            }

                            String strategy = (choice == 0) ? "overwrite" : "duplicate";
                            String note = "冲突副本，由 " + usernameField.getText().trim()
                                    + " 于 " + java.time.LocalDateTime.now() + " 生成";
                            String resolveResp = resolveConflictAPI(
                                    item.fileId,
                                    base64Content,
                                    strategy,
                                    note,
                                    compressCheckBox.isSelected(),
                                    encryptCheckBox.isSelected()
                            );

                            if (resolveResp.contains("\"success\":true")) {
                                successCount++;
                                handledByConflictAPI = true;
                                String msg = extractLatestInfo(resolveResp, "message");
                                String conflictName = extractLatestInfo(resolveResp, "conflictFilename");
                                uploadProgressArea.append("✅ 已通过冲突处理接口上传 " + file.getName() + "\n");
                                if (conflictName != null) {
                                    uploadProgressArea.append("ℹ️ 冲突文件命名为: " + conflictName + "\n");
                                }
                                if (msg != null) {
                                    shareResultArea.append("[冲突处理] " + msg + "\n");
                                }
                                logMessage("冲突上传响应: " + resolveResp);
                                getFileList();
                            } else {
                                failCount++;
                                uploadProgressArea.append("❌ 冲突处理失败: " + resolveResp + "\n");
                                logMessage("冲突处理失败: " + resolveResp);
                            }
                        }
                    }

                    if (handledByConflictAPI) {
                        continue;
                    }

                    uploadProgressArea.append("正在上传: " + file.getName() + " (" + formatFileSize(file.length()) + ")\n");

                    boolean success = client.uploadFile(
                        file.getName(),
                        targetPath,
                        base64Content,
                        compressCheckBox.isSelected(),
                        encryptCheckBox.isSelected()
                    );

                    if (success) {
                        successCount++;
                        uploadProgressArea.append("✅ " + file.getName() + " 上传成功\n");

                        Optional<FileItem> existing = findCachedFileByNameAndPath(file.getName(), targetPath);
                        if (existing.isPresent()) {
                            String createVersionResult = createFileVersion(existing.get().fileId, "自动上传创建版本");
                            uploadProgressArea.append("🔄 已为 " + file.getName() + " 创建新版本: " + createVersionResult + "\n");
                            logMessage("创建文件版本: " + createVersionResult);
                        } else {
                            uploadProgressArea.append("ℹ️ 新文件 " + file.getName() + " 已添加\n");
                        }
                    } else {
                        failCount++;
                        uploadProgressArea.append("❌ " + file.getName() + " 上传失败\n");
                    }
                } catch (Exception e) {
                    failCount++;
                    uploadProgressArea.append("❌ " + file.getName() + " 上传失败: " + e.getMessage() + "\n");
                }
            }

            fileResultArea.setText("上传完成！成功: " + successCount + " 个，失败: " + failCount + " 个");
            logMessage("文件上传完成: 成功 " + successCount + " 个，失败 " + failCount + " 个");

            getFileList();
        } catch (Exception e) {
            fileResultArea.setText("上传失败: " + e.getMessage());
            logMessage("上传失败: " + e.getMessage());
        }
    });
}
    
    /**
     * 获取文件列表
     */
    private void getFileList() {
        SwingUtilities.invokeLater(() -> {
            try {
                String response = client.getFileList("/");
                fileResultArea.setText(response);
                logMessage("获取文件列表: " + response);
                
                // 解析响应并显示在表格中
                parseAndDisplayFileList(response);
            } catch (Exception e) {
                fileResultArea.setText("获取文件列表失败: " + e.getMessage());
                logMessage("获取文件列表失败: " + e.getMessage());
            }
        });
    }
    
/**
 * 解析并显示文件列表
 */
private void parseAndDisplayFileList(String response) {
    fileTableModel.setRowCount(0);
    fileCache.clear();

    try {
        // 1) CS 协议形式：SUCCESS::id:filename:size[:storedSize]:createdAt:path;...
        if (response.startsWith("SUCCESS")) {
            String[] respParts = response.split("::", 2);
            if (respParts.length < 2) {
                return;
            }

            String fileListData = respParts[1];
            if (fileListData.trim().isEmpty()) {
                return;
            }

            String[] fileEntries = fileListData.split(";");
            for (String entry : fileEntries) {
                if (entry.trim().isEmpty()) {
                    continue;
                }

                // 旧: id:filename:size:createdAt:path
                // 新: id:filename:size:storedSize:createdAt:path
                String[] parts = entry.split(":", 6);
                if (parts.length < 5) {
                    continue;
                }

                long fileId;
                try {
                    fileId = Long.parseLong(parts[0]);
                } catch (NumberFormatException ignore) {
                    continue;
                }

                String fileName = parts[1];

                long fileSize = 0L;
                try {
                    fileSize = Long.parseLong(parts[2]);
                } catch (NumberFormatException ignore) {
                    // 保持默认 0
                }

                Long storedSize = null;
                String createdAtText;
                String filePath;

                if (parts.length == 5) {
                    // 旧格式：没有 storedSize
                    createdAtText = parts[3];
                    filePath = parts[4];
                } else {
                    // 新格式：第 3 个是 storedSize
                    try {
                        storedSize = Long.parseLong(parts[3]);
                    } catch (NumberFormatException ignore) {
                        storedSize = null;
                    }
                    createdAtText = parts[4];
                    filePath = parts[5];
                }

                LocalDateTime createdAt = parseDateTimeSafely(createdAtText);

                FileItem existing = fileCache.get(fileId);
                if (existing != null) {
                    if (isAfter(createdAt, existing.uploadedAt)) {
                        existing.filename = fileName;
                        existing.path = filePath;
                        existing.size = fileSize;
                        existing.storedSize = storedSize;
                        existing.uploadedAt = createdAt;
                    }
                    continue;
                }

                FileItem item = new FileItem();
                item.fileId = fileId;
                item.filename = fileName;
                item.path = filePath;
                item.size = fileSize;
                item.storedSize = storedSize;
                item.uploadedAt = createdAt;
                fileCache.put(fileId, item);
            }

            // 把缓存里的文件按时间倒序塞到表格里
            List<FileItem> items = new ArrayList<>(fileCache.values());
            items.sort(this::compareDateTimeDesc);

            for (FileItem item : items) {
                String sizeText = formatFileSize(item.size);
                if (item.storedSize != null && item.storedSize >= 0) {
                    sizeText = sizeText + " (存储: " + formatFileSize(item.storedSize) + ")";
                }

                fileTableModel.addRow(new Object[]{
                        item.filename,
                        sizeText,
                        item.path,
                        item.uploadedAt != null ? item.uploadedAt.toString() : "-",
                        String.valueOf(item.fileId),
                        "下载",
                        "删除",
                        "历史版本"
                });
            }
            return;
        }

        // 2) HTTP JSON 形式（原来的回退逻辑，保持不变）
        if (response.contains("\"success\":true") && response.contains("\"data\"")) {
            fileTableModel.addRow(new Object[]{
                    "文件列表获取成功",
                    "",
                    "",
                    LocalDateTime.now().toString(),
                    "",
                    "查看详情",
                    "-",
                    "-"
            });
        } else {
            fileTableModel.addRow(new Object[]{
                    "获取文件列表失败",
                    "",
                    "",
                    LocalDateTime.now().toString(),
                    "",
                    "重试",
                    "-",
                    "-"
            });
        }
    } catch (Exception e) {
        logMessage("解析文件列表失败: " + e.getMessage());
        fileTableModel.addRow(new Object[]{
                "解析失败: " + e.getMessage(),
                "",
                "",
                LocalDateTime.now().toString(),
                "",
                "重试",
                "-",
                "-"
        });
    }
}

    /**
     * 创建目录
     */
    private void createDirectory() {
        String dirName = JOptionPane.showInputDialog(this, "请输入目录名称:", "创建目录", JOptionPane.QUESTION_MESSAGE);
        if (dirName == null || dirName.trim().isEmpty()) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                String response = createDirectoryAPI(dirName);
                fileResultArea.setText(response);
                logMessage("创建目录: " + response);
            } catch (Exception e) {
                fileResultArea.setText("创建目录失败: " + e.getMessage());
                logMessage("创建目录失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 获取上传进度
     */
    private void getUploadProgress() {
        String uploadId = JOptionPane.showInputDialog(this, "请输入上传ID:", "查看上传进度", JOptionPane.QUESTION_MESSAGE);
        if (uploadId == null || uploadId.trim().isEmpty()) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                String response = getUploadProgressAPI(uploadId);
                fileResultArea.setText(response);
                logMessage("获取上传进度: " + response);
            } catch (Exception e) {
                fileResultArea.setText("获取上传进度失败: " + e.getMessage());
                logMessage("获取上传进度失败: " + e.getMessage());
            }
        });
    }
    
    // ==================== 同步功能 ====================
    
    /**
     * 开始同步
     */
    private void startSync() {
        SwingUtilities.invokeLater(() -> {
            try {
                boolean success = client.startSync(syncPathField.getText(), clientIdField.getText());
                if (success) {
                    syncResultArea.setText("同步已开始");
                    syncResultArea.setBackground(new Color(212, 237, 218));
                    logMessage("同步已开始");
                } else {
                    syncResultArea.setText("开始同步失败");
                    syncResultArea.setBackground(new Color(248, 215, 218));
                    logMessage("开始同步失败");
                }
            } catch (Exception e) {
                syncResultArea.setText("开始同步失败: " + e.getMessage());
                logMessage("开始同步失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 停止同步
     */
    private void stopSync() {
        SwingUtilities.invokeLater(() -> {
            try {
                boolean success = client.stopSync();
                if (success) {
                    syncResultArea.setText("同步已停止");
                    syncResultArea.setBackground(new Color(212, 237, 218));
                    logMessage("同步已停止");
                } else {
                    syncResultArea.setText("停止同步失败");
                    syncResultArea.setBackground(new Color(248, 215, 218));
                    logMessage("停止同步失败");
                }
            } catch (Exception e) {
                syncResultArea.setText("停止同步失败: " + e.getMessage());
                logMessage("停止同步失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 获取同步事件
     */
    private void getSyncEvents() {
        SwingUtilities.invokeLater(() -> {
            try {
                String response = client.getSyncEvents();
                syncResultArea.setText(response);
                logMessage("获取同步事件: " + response);
            } catch (Exception e) {
                syncResultArea.setText("获取同步事件失败: " + e.getMessage());
                logMessage("获取同步事件失败: " + e.getMessage());
            }
        });
    }

    // ==================== 云端通知功能 ====================

private void getCloudNotifications() {
    if (!client.isLoggedIn()) {
        syncResultArea.setText("请先登录才能查看云端通知");
        logMessage("获取云端通知失败: 未登录");
        return;
    }
    SwingUtilities.invokeLater(() -> {
        try {
            NotificationResponse response = getCloudNotificationsAPI();
            if (response == null) {
                syncResultArea.setText("获取云端通知失败: 服务端无响应");
                logMessage("获取云端通知失败: 服务端无响应");
                return;
            }
            if (!response.success) {
                String msg = response.message == null ? "未知错误" : response.message;
                syncResultArea.setText("获取云端通知失败: " + msg);
                logMessage("获取云端通知失败: " + msg);
                return;
            }
            if (response.lastEventId != null) {
                lastNotificationId = response.lastEventId;   // ✅ 记录最后一次事件ID，用于增量拉取
            }
            String formatted = formatNotificationResponse(response.events);
            syncResultArea.setText(formatted);
            logMessage("云端通知: " + formatted);
        } catch (Exception e) {
            syncResultArea.setText("获取云端通知失败: " + e.getMessage());
            logMessage("获取云端通知失败: " + e.getMessage());
        }
    });
}

private NotificationResponse getCloudNotificationsAPI() throws IOException {
    StringBuilder urlBuilder =
            new StringBuilder("http://localhost:8080/api/sync/notifications");
    boolean hasQuery = false;

    // 增量拉取：带上 sinceId
    if (lastNotificationId > 0) {
        urlBuilder.append("?sinceId=").append(lastNotificationId);
        hasQuery = true;
    }

    // 是否排除本客户端触发的事件
    String clientId = clientIdField.getText() == null ? "" : clientIdField.getText().trim();
    boolean excludeSelf = excludeSelfNotificationsCheckBox != null
            && excludeSelfNotificationsCheckBox.isSelected();
    if (excludeSelf && !clientId.isEmpty()) {
        urlBuilder.append(hasQuery ? "&" : "?")
                .append("clientId=")
                .append(java.net.URLEncoder.encode(clientId, "UTF-8"));
        hasQuery = true;
    }

    java.net.URL url = new java.net.URL(urlBuilder.toString());
    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
    conn.setRequestProperty("Accept", "application/json");

    int responseCode = conn.getResponseCode();
    java.io.InputStream stream = responseCode >= 200 && responseCode < 300
            ? conn.getInputStream()
            : conn.getErrorStream();

    try (java.io.BufferedReader br = new java.io.BufferedReader(
            new java.io.InputStreamReader(stream, "utf-8"))) {
        // 直接用 ObjectMapper 把 JSON 映射到 NotificationResponse
        return objectMapper.readValue(br, NotificationResponse.class);
    }
}

/**
 * 把通知列表格式化成易读文本，显示在 syncResultArea
 */
private String formatNotificationResponse(java.util.List<NotificationEvent> events) {
    if (events == null || events.isEmpty()) {
        return "暂无新的云端通知";
    }
    java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (NotificationEvent event : events) {
        String displayPath = extractSyncPath(event.filePath);
        if (displayPath == null) {
            continue;
        }
        if (count == 0) {
            sb.append("云端有以下更新:\n");
        }
        sb.append("• [").append(event.eventType).append("] ").append(displayPath);
        if (event.eventTime != null) {
            try {
                java.time.LocalDateTime time = java.time.LocalDateTime.parse(event.eventTime);
                sb.append(" @ ").append(time.format(formatter));
            } catch (Exception ignored) {
                sb.append(" @ ").append(event.eventTime);
            }
        }
        sb.append('\n');
        count++;
    }
    if (count == 0) {
        return "暂无新的云端通知";
    }
    return sb.toString();
}

/**
 * 把后端路径裁剪成展示用的相对路径（比如从 sync/ 开始）
 */
private String extractSyncPath(String rawPath) {
    if (rawPath == null || rawPath.isEmpty()) {
        return null;
    }
    String normalized = rawPath.replace('\\', '/');
    int idx = normalized.indexOf("sync/");
    if (idx == -1) {
        return null;
    }
    return normalized.substring(idx);
}


// ==================== 云端通知数据结构 ====================

private static class NotificationResponse {
    public boolean success;
    public String message;
    public Long lastEventId;
    public java.util.List<NotificationEvent> events;
}

private static class NotificationEvent {
    public long id;
    public String eventType;
    public String filePath;
    public String oldPath;
    public Long fileSize;
    public String fileHash;
    public String clientId;
    public String eventTime;
}

    
    // ==================== 下载功能 ====================
    
   // --- 统一下载入口的对外调用 ---
private void downloadSingleFile() {
    String fileId = fileIdField.getText().trim();
    if (fileId.isEmpty()) {
        JOptionPane.showMessageDialog(this, "请输入文件ID", "提示", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("选择保存位置");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        downloadFileToPath(Long.parseLong(fileId), chooser.getSelectedFile().getAbsolutePath());
    }
}

private void downloadMultiThread() {
    String fileId = fileIdField.getText().trim();
    String threads = downloadThreadsField.getText().trim();
    if (fileId.isEmpty()) {
        JOptionPane.showMessageDialog(this, "请输入文件ID", "提示", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("选择保存位置");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        String savePath = chooser.getSelectedFile().getAbsolutePath();
        downloadFileToPath(Long.parseLong(fileId), savePath);

        SwingUtilities.invokeLater(() -> {
            try {
                String response = downloadMultiThreadAPI(Long.parseLong(fileId), Integer.parseInt(threads));
                
                downloadResultArea.setText("多线程下载完成，保存到: " + savePath + "\n" + response);
                logMessage("多线程下载: " + response);
            } catch (Exception e) {
                downloadResultArea.setText("多线程下载失败: " + e.getMessage());
                logMessage("多线程下载失败: " + e.getMessage());
            }
        });
    }
}

private void downloadSharedFile(ShareItem item) {
    if (!client.isLoggedIn()) {
        JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.INFORMATION_MESSAGE);
        return;
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("选择下载目录");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
    }
    File targetDir = chooser.getSelectedFile();
    if (targetDir == null || !targetDir.isDirectory()) {
        JOptionPane.showMessageDialog(this, "请选择有效的目录", "提示", JOptionPane.WARNING_MESSAGE);
        return;
    }
    downloadFileToPath(item.fileId, targetDir.getAbsolutePath());
}
    
    
    /**
     * 批量下载
     */
    private void downloadBatchFiles() {
        String fileIds = batchFileIdsField.getText().trim();
        String format = (String) downloadFormatCombo.getSelectedItem();
        
        if (fileIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入文件ID列表", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 选择保存位置
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择保存位置");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String savePath = fileChooser.getSelectedFile().getAbsolutePath();
            
            SwingUtilities.invokeLater(() -> {
                try {
                    String response = downloadBatchFilesAPI(fileIds, format);
                    downloadResultArea.setText("批量下载完成，保存到: " + savePath + "\n" + response);
                    logMessage("批量下载: " + response);
                } catch (Exception e) {
                    downloadResultArea.setText("批量下载失败: " + e.getMessage());
                    logMessage("批量下载失败: " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * 生成下载链接
     */
    private void generateDownloadLink() {
        String fileId = fileIdField.getText().trim();
        String expireMinutes = linkExpireField.getText().trim();
        
        if (fileId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入文件ID", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                String response = generateDownloadLinkAPI(Long.parseLong(fileId), Integer.parseInt(expireMinutes));
                downloadResultArea.setText(response);
                logMessage("生成下载链接: " + response);
            } catch (Exception e) {
                downloadResultArea.setText("生成下载链接失败: " + e.getMessage());
                logMessage("生成下载链接失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 获取我的文件
     */
    private void getMyFiles() {
        SwingUtilities.invokeLater(() -> {
            try {
                String response = getMyFilesAPI();
                displayMyFiles(response);
                logMessage("获取我的文件: " + response);
            } catch (Exception e) {
                downloadResultArea.setText("获取我的文件失败: " + e.getMessage());
                logMessage("获取我的文件失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 显示我的文件列表
     */
    private void displayMyFiles(String response) {
        try {
            // 清空下载文件表格
            downloadTableModel.setRowCount(0);
            
            // 解析JSON响应
            if (response.contains("\"success\":true")) {
                // 使用正则表达式提取JSON对象
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{[^}]*\"id\"[^}]*\\}");
                java.util.regex.Matcher matcher = pattern.matcher(response);
                
                while (matcher.find()) {
                    String fileObj = matcher.group();
                    
                    // 解析每个文件对象
                    String id = extractValue(fileObj, "id");
                    String filename = extractValue(fileObj, "filename");
                    String filePath = extractValue(fileObj, "filePath");
                    String fileSize = extractValue(fileObj, "fileSize");
                    String createdAt = extractValue(fileObj, "createdAt");
                    
                    if (filename != null && !filename.isEmpty()) {
                        downloadTableModel.addRow(new Object[]{
                            filename,
                            fileSize != null ? formatFileSize(Long.parseLong(fileSize)) : "未知大小",
                            filePath != null ? filePath : "/",
                            createdAt != null ? createdAt : java.time.LocalDateTime.now().toString(),
                            id != null ? id : "",
                            "下载"
                        });
                    }
                }
                
                downloadResultArea.setText("文件列表获取成功，共 " + downloadTableModel.getRowCount() + " 个文件");
                downloadResultArea.setBackground(new Color(212, 237, 218));
            } else {
                downloadResultArea.setText("获取文件列表失败: " + response);
                downloadResultArea.setBackground(new Color(248, 215, 218));
            }
        } catch (Exception e) {
            downloadResultArea.setText("解析文件列表失败: " + e.getMessage());
            logMessage("解析文件列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 从JSON字符串中提取值
     */
    private String extractValue(String json, String key) {
        try {
            // 先尝试匹配字符串值
            String stringPattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern stringP = java.util.regex.Pattern.compile(stringPattern);
            java.util.regex.Matcher stringM = stringP.matcher(json);
            if (stringM.find()) {
                return stringM.group(1);
            }
            
            // 再尝试匹配数字值
            String numberPattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
            java.util.regex.Pattern numberP = java.util.regex.Pattern.compile(numberPattern);
            java.util.regex.Matcher numberM = numberP.matcher(json);
            if (numberM.find()) {
                return numberM.group(1);
            }
            
            // 最后匹配布尔值
            String booleanPattern = "\"" + key + "\"\\s*:\\s*(true|false)";
            java.util.regex.Pattern booleanP = java.util.regex.Pattern.compile(booleanPattern);
            java.util.regex.Matcher booleanM = booleanP.matcher(json);
            if (booleanM.find()) {
                return booleanM.group(1);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return null;
    }

    private String extractValue(String json, String parentKey, String childKey) {
    String objectPattern = "\"" + parentKey + "\"\\s*:\\s*\\{([^{}]*)\\}";
    java.util.regex.Matcher matcher =
            java.util.regex.Pattern.compile(objectPattern).matcher(json);
    if (matcher.find()) {
        String nestedJson = "{" + matcher.group(1) + "}";
        return extractValue(nestedJson, childKey);
    }
    return null;
}
    
    // ==================== API调用方法 ====================
    
    private String registerUser(String username, String password, String email) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/auth/register");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            String jsonInputString = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"email\":\"%s\"}", 
                username, password, email
            );
            
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "注册失败: " + e.getMessage();
        }
    }


    private String createShareAPI(long fileId, String shareWith, String permission, Integer expireDays) {
    try {
        java.net.URL url = new java.net.URL("http://localhost:8080/api/share/create");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        StringBuilder form = new StringBuilder();
        form.append("fileId=").append(fileId)
            .append("&shareWith=").append(java.net.URLEncoder.encode(shareWith, "UTF-8"))
            .append("&permission=").append(permission);
        if (expireDays != null) {
            form.append("&expireDays=").append(expireDays);
        }
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(form.toString().getBytes("utf-8"));
        }
        return readResponse(conn);
    } catch (Exception e) {
        return "创建共享失败: " + e.getMessage();
    }
}

    private String checkUploadConflictAPI(long fileId, Integer clientVersion, String clientHash) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/files/conflict-check");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String payload = String.format(
                "{\"fileId\":%d,\"clientVersion\":%s,\"clientHash\":%s}",
                fileId,
                clientVersion != null ? clientVersion.toString() : "null",
                clientHash != null ? "\"" + clientHash + "\"" : "null"
            );

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            return readResponse(conn);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"调用冲突检查失败: " + e.getMessage() + "\"}";
        }
    }

    private String resolveConflictAPI(long fileId,
                                  String base64Content,
                                  String strategy,
                                  String note,
                                  boolean compress,
                                  boolean encrypt) {
    try {
        java.net.URL url = new java.net.URL("http://localhost:8080/api/files/resolve-conflict");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String safeNote = note == null ? "" : note.replace("\"", "\\\"");
        String payload = new StringBuilder()
            .append("{\"fileId\":").append(fileId)
            .append(",\"base64Content\":\"").append(base64Content).append("\"")
            .append(",\"strategy\":\"").append(strategy).append("\"")
            .append(",\"note\":\"").append(safeNote).append("\"")
            .append(",\"compress\":").append(compress)
            .append(",\"encrypt\":").append(encrypt)
            .append("}")
            .toString();

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        return readResponse(conn);
    } catch (Exception e) {
        return "{\"success\":false,\"message\":\"调用冲突处理失败: " + e.getMessage() + "\"}";
    }
}

private String getMySharesAPI() {
    try {
        java.net.URL url = new java.net.URL("http://localhost:8080/api/share/my-shares");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
        return readResponse(conn);
    } catch (Exception e) {
        return "获取我的共享失败: " + e.getMessage();
    }
}

private String getSharedWithMeAPI() {
    try {
        java.net.URL url = new java.net.URL("http://localhost:8080/api/share/shared-with-me");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
        return readResponse(conn);
    } catch (Exception e) {
        return "获取共享给我的文件失败: " + e.getMessage();
    }
}

private String cancelShareAPI(long shareId) {
    try {
        java.net.URL url = new java.net.URL("http://localhost:8080/api/share/cancel/" + shareId);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
        return readResponse(conn);
    } catch (Exception e) {
        return "取消共享失败: " + e.getMessage();
    }
}

    private String readResponse(java.net.HttpURLConnection conn) {
        try {
            int responseCode = conn.getResponseCode();
            java.io.InputStream stream = responseCode >= 200 && responseCode < 300
                ? conn.getInputStream()
                : conn.getErrorStream();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                if (responseCode >= 200 && responseCode < 300) {
                    return response.toString();
                }
                return "ERROR::" + responseCode + "::" + response.toString();
            }
        } catch (Exception e) {
            return "ERROR::" + e.getMessage();
        }
    }
    
    private String createDirectoryAPI(String dirName) {
        try {
            // 使用正确的API端点和参数格式
            String encodedPath = java.net.URLEncoder.encode("/" + dirName, "UTF-8");
            java.net.URL url = new java.net.URL("http://localhost:8080/api/files/mkdir?path=" + encodedPath);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine = null;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    return response.toString();
                }
            } else {
                return "创建目录失败: HTTP " + responseCode;
            }
        } catch (Exception e) {
            return "创建目录失败: " + e.getMessage();
        }
    }
    
    private String getUploadProgressAPI(String uploadId) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/resume/progress/" + uploadId);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "获取上传进度失败: " + e.getMessage();
        }
    }
    
    private String downloadMultiThreadAPI(Long fileId, int threads) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/files/download-multithread/" + fileId + "?threads=" + threads);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine = null;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    return response.toString();
                }
            } else {
                // 读取错误响应
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String responseLine = null;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }
                    return "多线程下载失败: HTTP " + responseCode + " - " + errorResponse.toString();
                }
            }
        } catch (Exception e) {
            return "多线程下载失败: " + e.getMessage();
        }
    }
    
    
    private String downloadBatchFilesAPI(String fileIds, String format) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/files/download-batch");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            conn.setDoOutput(true);
            
            String formData = "fileIds=" + java.net.URLEncoder.encode(fileIds, "UTF-8") + 
                             "&format=" + java.net.URLEncoder.encode(format, "UTF-8");
            
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = formData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "批量下载失败: " + e.getMessage();
        }
    }
    
    private String generateDownloadLinkAPI(Long fileId, int expireMinutes) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/files/download-link/" + fileId + "?expireMinutes=" + expireMinutes);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "生成下载链接失败: " + e.getMessage();
        }
    }
    
    private String getMyFilesAPI() {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/files/list");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "获取我的文件失败: " + e.getMessage();
        }
    }
    
    private String getFileVersionsAPI(long fileId) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/version/list/" + fileId);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            
            int responseCode = conn.getResponseCode();
            java.io.InputStream stream = responseCode >= 200 && responseCode < 300 
                ? conn.getInputStream() 
                : conn.getErrorStream();
            
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "获取版本列表失败: " + e.getMessage();
        }
    }
    
    private String createVersionAPI(long fileId, String description) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/version/create");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            
            StringBuilder formData = new StringBuilder();
            formData.append("fileId=").append(fileId);
            if (description != null && !description.isEmpty()) {
                formData.append("&changeDescription=").append(java.net.URLEncoder.encode(description, "UTF-8"));
            }
            
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = formData.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            java.io.InputStream stream = responseCode >= 200 && responseCode < 300 
                ? conn.getInputStream() 
                : conn.getErrorStream();
            
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
            catch (Exception e) {
                return "创建版本失败: "+e.getMessage();
            }
        }
        catch (Exception e) {
            return "创建版本失败: "+e.getMessage();
        }
    }
    

    private String deleteVersionAPI(long fileId, int versionNumber) {
    try {
        java.net.URL url = new java.net.URL(
                "http://localhost:8080/api/version/delete/" + fileId + "/" + versionNumber);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + client.getToken());

        int responseCode = conn.getResponseCode();
        java.io.InputStream stream = responseCode >= 200 && responseCode < 300
                ? conn.getInputStream()
                : conn.getErrorStream();

        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(stream, "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            return response.toString();
        }
    } catch (Exception e) {
        return "删除版本失败: " + e.getMessage();
    }
}

    private String rollbackVersionAPI(long fileId, int versionNumber) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/version/rollback");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            
            String formData = "fileId=" + fileId + "&versionNumber=" + versionNumber;
            
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = formData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            java.io.InputStream stream = responseCode >= 200 && responseCode < 300 
                ? conn.getInputStream() 
                : conn.getErrorStream();
            
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "回滚版本失败: " + e.getMessage();
        }
    }
    
    private void downloadFileById(long fileId) {
        if (!client.isLoggedIn()) {
            JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择保存位置");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String savePath = fileChooser.getSelectedFile().getAbsolutePath();
            SwingUtilities.invokeLater(() -> downloadFileToPath(fileId, savePath));
        }
    }

    /**
 * 通过ID删除文件
 */
private void deleteFileById(long fileId) {
    if (!client.isLoggedIn()) {
        JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    int result = JOptionPane.showConfirmDialog(
            this,
            "确定要删除文件ID: " + fileId + " 吗？",
            "确认删除",
            JOptionPane.YES_NO_OPTION
    );
    if (result != JOptionPane.YES_OPTION) {
        return;
    }

    SwingUtilities.invokeLater(() -> {
        try {
            String response = deleteFileAPI(fileId);
            fileResultArea.setText(response);
            logMessage("删除文件: " + response);

            // 同步刷新两个列表：文件管理 tab 和 下载 tab
            getFileList();
            getMyFiles();
        } catch (Exception e) {
            String msg = "删除文件失败: " + e.getMessage();
            fileResultArea.setText(msg);
            logMessage(msg);
            JOptionPane.showMessageDialog(this, msg, "错误", JOptionPane.ERROR_MESSAGE);
        }
    });
}

/**
 * 删除文件 API
 */
private String deleteFileAPI(long fileId) {
    try {
        java.net.URL url = new java.net.URL("http://localhost:8080/api/files/" + fileId);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + client.getToken());

        // 使用统一的响应处理工具
        return readResponse(conn);
    } catch (Exception e) {
        return "删除文件失败: " + e.getMessage();
    }
}


    

        // ==== 历史版本对话框主流程 ====
    private void showHistoryDialog(long fileId) {
        if (!client.isLoggedIn()) {
            JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        FileItem item = fileCache.get(fileId);
        if (item == null) {
            logMessage("文件ID " + fileId + " 不在缓存中，尝试刷新列表...");
            getFileList();
            item = fileCache.get(fileId);
            if (item == null) {
                JOptionPane.showMessageDialog(this, "未找到文件记录，请稍后重试", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        historyDialogFileId = fileId;
        historyDialogFileName = item.filename;

        if (historyDialog == null) {
            historyDialog = new JDialog(this, "历史版本 - " + item.filename, true);
            historyDialog.setLayout(new BorderLayout(10, 10));

            JScrollPane tableScrollPane = new JScrollPane(historyTable);
            tableScrollPane.setBorder(new TitledBorder("历史版本列表"));
            historyDialog.add(tableScrollPane, BorderLayout.CENTER);

            JScrollPane detailScrollPane = new JScrollPane(historyDetailArea);
            detailScrollPane.setBorder(new TitledBorder("版本详情"));
            historyDialog.add(detailScrollPane, BorderLayout.SOUTH);

            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton refreshButton = new JButton("🔄 刷新");
            refreshButton.addActionListener(e -> loadHistoryVersions(historyDialogFileId, true));
            JButton closeButton = new JButton("关闭");
            closeButton.addActionListener(e -> historyDialog.setVisible(false));
            topPanel.add(refreshButton);
            topPanel.add(closeButton);
            historyDialog.add(topPanel, BorderLayout.NORTH);

            historyDialog.setSize(900, 520);
            historyDialog.setLocationRelativeTo(this);
        } else {
            historyDialog.setTitle("历史版本 - " + item.filename);
        }

        loadHistoryVersions(fileId, false);
        historyDialog.setVisible(true);
    }

    // ==== 历史版本数据加载/解析 ====
    private void loadHistoryVersions(long fileId, boolean showMessage) {
        historyDetailArea.setText("正在加载历史版本...");
        historyTableModel.setRowCount(0);

        SwingUtilities.invokeLater(() -> {
            try {
                String response = getFileVersionsAPI(fileId);
                List<VersionItem> versions = parseVersionList(response, fileId);
                populateHistoryTable(versions);

                if (versions.isEmpty()) {
                    historyDetailArea.setText("未找到历史版本");
                } else {
                    historyDetailArea.setText(String.format("文件: %s%n总计: %d 个版本",
                            historyDialogFileName, versions.size()));
                }

                if (showMessage) {
                    JOptionPane.showMessageDialog(historyDialog, "历史版本已刷新", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception e) {
                historyDetailArea.setText("加载历史版本失败: " + e.getMessage());
                logMessage("历史版本加载失败: " + e.getMessage());
            }
        });
    }

    private List<VersionItem> parseVersionList(String response, long fileId) {
        List<VersionItem> versions = new ArrayList<>();
        if (response == null) {
            return versions;
        }

        if (!response.contains("\"success\":true")) {
            historyDetailArea.setText(response);
            return versions;
        }

        try {
            int versionsKeyIndex = response.indexOf("\"versions\"");
            if (versionsKeyIndex == -1) {
                historyDetailArea.setText("响应中未找到版本数据");
                return versions;
            }

            int arrayStart = response.indexOf("[", versionsKeyIndex);
            int arrayEnd = response.indexOf("]", arrayStart);
            if (arrayStart == -1 || arrayEnd == -1 || arrayEnd <= arrayStart) {
                historyDetailArea.setText("版本数据格式错误");
                return versions;
            }

            String versionsArray = response.substring(arrayStart + 1, arrayEnd);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{[^{}]*\\}");
            java.util.regex.Matcher matcher = pattern.matcher(versionsArray);

            while (matcher.find()) {
                String versionJson = matcher.group();
                VersionItem item = new VersionItem();

                String id = extractValue(versionJson, "id");
                String versionNumber = extractValue(versionJson, "versionNumber");
                String fileSize = extractValue(versionJson, "fileSize");
                String fileHash = extractValue(versionJson, "fileHash");
                if (fileHash == null) {
                    fileHash = extractValue(versionJson, "hash");
                }
                String description = extractValue(versionJson, "changeDescription");
                if (description == null) {
                    description = extractValue(versionJson, "description");
                }
                String createdAt = extractValue(versionJson, "createdAt");
                String currentFlag = extractValue(versionJson, "isCurrent");
                if (currentFlag == null) {
                    currentFlag = extractValue(versionJson, "current");
                }

                item.versionId = id != null ? Long.parseLong(id) : -1;
                item.versionNumber = versionNumber != null ? Integer.parseInt(versionNumber) : -1;
                item.size = fileSize != null ? Long.parseLong(fileSize) : 0L;
                item.hash = fileHash != null ? fileHash : "-";
                item.description = description != null ? description : "-";
                item.createdAt = createdAt != null ? parseDateTimeSafely(createdAt) : null;
                item.current = currentFlag != null && Boolean.parseBoolean(currentFlag);

                versions.add(item);
            }

            versions.sort((a, b) -> Integer.compare(b.versionNumber, a.versionNumber));
            fileCache.computeIfPresent(fileId, (id, cacheItem) -> {
                cacheItem.versions = versions;
                return cacheItem;
            });
        } catch (Exception e) {
            historyDetailArea.setText("解析历史版本失败: " + e.getMessage());
            logMessage("解析历史版本失败: " + e.getMessage());
        }

        return versions;
    }

    private void populateHistoryTable(List<VersionItem> versions) {
        historyTableModel.setRowCount(0);
        if (versions.isEmpty()) {
            historyTableModel.addRow(new Object[]{"-", "-", "-", "-", "-", "-", "-", "-"});
            return;
        }

        for (VersionItem version : versions) {
            historyTableModel.addRow(new Object[]{
                version.versionNumber,
                formatFileSize(version.size),
                version.hash,
                version.description,
                version.createdAt != null ? version.createdAt.toString() : "-",
                version.current ? "是" : "否",
                "下载",
                "回滚",
                "删除"
            });
        }
    }

    // ==== 详情展示与操作 ====
    private void updateHistoryDetail(int selectedRow) {
        if (selectedRow < 0 || selectedRow >= historyTableModel.getRowCount()) {
            historyDetailArea.setText("");
            return;
        }

        Object versionNumberObj = historyTableModel.getValueAt(selectedRow, 0);
        int versionNumber = Integer.parseInt(versionNumberObj.toString());
        FileItem cached = fileCache.get(historyDialogFileId);
        if (cached == null || cached.versions == null) {
            historyDetailArea.setText("");
            return;
        }

        cached.versions.stream()
            .filter(v -> v.versionNumber == versionNumber)
            .findFirst()
            .ifPresent(version -> historyDetailArea.setText(String.format(
                "文件: %s%n版本号: %d%n大小: %s%n哈希: %s%n描述: %s%n创建时间: %s%n是否当前: %s",
                historyDialogFileName,
                version.versionNumber,
                formatFileSize(version.size),
                version.hash,
                version.description,
                version.createdAt != null ? version.createdAt.toString() : "-",
                version.current ? "是" : "否")));
    }


    private void handleHistoryAction(int row, HistoryActionType actionType) {
    if (row < 0 || row >= historyTableModel.getRowCount()) {
        return;
    }

    Object versionNumberObj = historyTableModel.getValueAt(row, 0);
    int versionNumber = Integer.parseInt(versionNumberObj.toString());
    FileItem cached = fileCache.get(historyDialogFileId);
    if (cached == null) {
        JOptionPane.showMessageDialog(historyDialog, "未找到缓存的文件信息", "提示", JOptionPane.WARNING_MESSAGE);
        return;
    }

    VersionItem version = cached.versions.stream()
        .filter(v -> v.versionNumber == versionNumber)
        .findFirst()
        .orElse(null);

    if (version == null) {
        JOptionPane.showMessageDialog(historyDialog, "未找到版本信息", "提示", JOptionPane.WARNING_MESSAGE);
        return;
    }

    switch (actionType) {
        case DOWNLOAD:
            performVersionDownload(version);
            break;
        case ROLLBACK:
            performVersionRollback(version);
            break;
        case DELETE:
            performVersionDelete(version);
            break;
        default:
            break;
    }
}

/**
 * 删除指定历史版本：调用后端 + 更新缓存 + 刷新表格
 */
private void performVersionDelete(VersionItem version) {
    // 一般不允许删除当前版本
    if (version.current) {
        JOptionPane.showMessageDialog(historyDialog,
                "当前版本不能删除，如需删除请先回滚到其他版本。",
                "提示",
                JOptionPane.WARNING_MESSAGE);
        return;
    }

    int confirm = JOptionPane.showConfirmDialog(historyDialog,
            "确定要删除版本 " + version.versionNumber + " 吗？此操作不可恢复！",
            "确认删除版本",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (confirm != JOptionPane.YES_OPTION) {
        return;
    }

    try {
        String response = deleteVersionAPI(historyDialogFileId, version.versionNumber);
        logMessage("删除历史版本: " + response);

        boolean ok = response != null && response.contains("\"success\":true");
        if (!ok) {
            JOptionPane.showMessageDialog(historyDialog,
                    response,
                    "删除失败",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 1）更新本地缓存
        FileItem cached = fileCache.get(historyDialogFileId);
        if (cached != null && cached.versions != null) {
            cached.versions.removeIf(v -> v.versionNumber == version.versionNumber);
        }

        // 2）本地表格立刻删掉对应行（用户马上看得到变化）
        for (int i = 0; i < historyTableModel.getRowCount(); i++) {
            Object vObj = historyTableModel.getValueAt(i, 0);
            if (vObj != null && vObj.toString().equals(String.valueOf(version.versionNumber))) {
                historyTableModel.removeRow(i);
                break;
            }
        }

        // 3）重新从服务器拉一遍，保证和后端 100% 一致
        loadHistoryVersions(historyDialogFileId, false);

        JOptionPane.showMessageDialog(historyDialog,
                "版本删除成功",
                "删除结果",
                JOptionPane.INFORMATION_MESSAGE);

    } catch (Exception e) {
        JOptionPane.showMessageDialog(historyDialog,
                "删除版本失败: " + e.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
        logMessage("删除历史版本失败: " + e.getMessage());
    }
}



    private void performVersionDownload(VersionItem version) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择保存位置");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showSaveDialog(historyDialog);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String savePath = chooser.getSelectedFile().getAbsolutePath();

        SwingUtilities.invokeLater(() -> {
            try {
                java.net.URL url = new java.net.URL(
                    "http://localhost:8080/api/version/download/" + historyDialogFileId + "/" + version.versionNumber);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + client.getToken());

int responseCode = conn.getResponseCode();
if (responseCode == 200) {
    String contentDisposition = conn.getHeaderField("Content-Disposition");
    String filename = historyDialogFileName + "_v" + version.versionNumber;

    if (contentDisposition != null && contentDisposition.contains("filename=")) {
        // 取出 filename= 后面的部分
        String tmp = contentDisposition.substring(contentDisposition.indexOf("filename=") + "filename=".length()).trim();
        // 去掉分号后面的内容（有些头是 filename="xxx"; filename*=UTF-8''xxx）
        int semicolonIndex = tmp.indexOf(';');
        if (semicolonIndex >= 0) {
            tmp = tmp.substring(0, semicolonIndex).trim();
        }
        // 去掉两边的引号
        if ((tmp.startsWith("\"") && tmp.endsWith("\"")) || (tmp.startsWith("'") && tmp.endsWith("'"))) {
            tmp = tmp.substring(1, tmp.length() - 1);
        }
        filename = tmp;
    }

    // 再保险一点，把路径分隔符替换掉，防止非法字符
    filename = filename.replace("\\", "_").replace("/", "_");

    File dir = new File(savePath);
    if (!dir.exists()) {
        dir.mkdirs();
    }
    File outFile = new File(dir, filename);

    try (java.io.InputStream is = conn.getInputStream();
         java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
    }

    JOptionPane.showMessageDialog(historyDialog, "版本下载成功: " + filename, "成功",
            JOptionPane.INFORMATION_MESSAGE);
    logMessage("历史版本下载成功: " + filename);
} else {
    String error = readErrorResponse(conn);
    JOptionPane.showMessageDialog(historyDialog, "版本下载失败: " + error, "错误",
            JOptionPane.ERROR_MESSAGE);
    logMessage("历史版本下载失败: " + error);
}

            } catch (Exception e) {
                JOptionPane.showMessageDialog(historyDialog, "版本下载失败: " + e.getMessage(), "错误",
                        JOptionPane.ERROR_MESSAGE);
                logMessage("历史版本下载失败: " + e.getMessage());
            }
        });
    }

    private void performVersionRollback(VersionItem version) {
        int confirm = JOptionPane.showConfirmDialog(historyDialog,
            "确定将文件回滚到版本 " + version.versionNumber + " 吗？",
            "确认回滚",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                String response = rollbackVersionAPI(historyDialogFileId, version.versionNumber);
                logMessage("回滚历史版本: " + response);
                JOptionPane.showMessageDialog(historyDialog, response, "回滚结果", JOptionPane.INFORMATION_MESSAGE);
                getFileList();
                loadHistoryVersions(historyDialogFileId, false);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(historyDialog, "回滚失败: " + e.getMessage(), "错误",
                        JOptionPane.ERROR_MESSAGE);
                logMessage("历史版本回滚失败: " + e.getMessage());
            }
        });
    }

    private String readErrorResponse(java.net.HttpURLConnection conn) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getErrorStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            return response.toString();
        } catch (Exception ex) {
            try {
                return "HTTP " + conn.getResponseCode();
            } catch (Exception ignored) {
                return "HTTP 错误";
            }
        }
    }

    
    
    // ==================== 工具方法 ====================
    private Optional<FileItem> findCachedFileByNameAndPath(String filename, String path) {
    return fileCache.values().stream()
        .filter(item -> filename.equals(item.filename) && path.equals(item.path))
        .findFirst();
}

    private String calculateFileHash(File file) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String createFileVersion(long fileId, String description) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/api/version/create");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + client.getToken());
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            StringBuilder formData = new StringBuilder();
            formData.append("fileId=").append(fileId);
            if (description != null && !description.isEmpty()) {
                formData.append("&changeDescription=")
                    .append(java.net.URLEncoder.encode(description, "UTF-8"));
            }

            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = formData.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            java.io.InputStream stream = responseCode >= 200 && responseCode < 300
                ? conn.getInputStream()
                : conn.getErrorStream();

            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "创建版本失败: " + e.getMessage();
        }
    }    

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes == 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = (int) (Math.log(bytes) / Math.log(1024));
        double size = bytes / Math.pow(1024, unitIndex);
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    private LocalDateTime parseDateTimeSafely(String value) {
    if (value == null || value.isEmpty()) {
        return null;
    }
    try {
        return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
    } catch (DateTimeParseException ignore) {
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}

    private boolean isAfter(LocalDateTime lhs, LocalDateTime rhs) {
        if (lhs == null) {
            return false;
        }
        if (rhs == null) {
            return true;
        }
        return lhs.isAfter(rhs);
    }

    private int compareDateTimeDesc(FileItem a, FileItem b) {
        LocalDateTime left = a.uploadedAt;
        LocalDateTime right = b.uploadedAt;
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }


    
    
    /**
     * 记录日志
     */
    private void logMessage(String message) {
        logArea.append(java.time.LocalDateTime.now() + " - " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
    
    /**
     * 按钮渲染器
     */
    class ButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }
        
        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                                                              boolean isSelected, boolean hasFocus,
                                                              int row, int column) {
            setText(value == null ? "" : value.toString());
            return this;
        }
    }
    
    /**
     * 按钮编辑器
     */
    class ButtonEditor extends DefaultCellEditor {
        private final JButton button;
        private ButtonAction action;
        private JTable currentTable;
        private int currentRow;
        private int currentColumn;
        private boolean isPushed;

        public ButtonEditor(JCheckBox checkBox, ButtonAction action) {
            super(checkBox);
            this.action = action;
            this.button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> fireEditingStopped());
        }

        public ButtonEditor(JCheckBox checkBox) {
            this(checkBox, ButtonAction.DOWNLOAD);
        }
        
        
       // --- ButtonEditor 对共享操作的支持 ---
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                boolean isSelected, int row, int column) {
        button.setText(value == null ? "操作" : value.toString());
        currentTable = table;
        currentRow = row;
        currentColumn = column;

        if (table == fileListTable) {
    if (column == 5) {
        action = ButtonAction.DOWNLOAD;   // 下载
    } else if (column == 6) {
        action = ButtonAction.DELETE;     // 删除
    } else {
        action = ButtonAction.HISTORY;    // 历史版本
    }
} else if (table == downloadFileTable) {
    action = ButtonAction.DOWNLOAD;
} else if (table == myShareTable) {
    if (column == 7) {
        action = ButtonAction.DOWNLOAD_MY_SHARE;
    } else if (column == 8) {
        action = ButtonAction.UPLOAD_MY_SHARE;
    } else if (column == 9) {
        action = ButtonAction.HISTORY_SHARED;
    } else {
        action = ButtonAction.CANCEL_SHARE;
    }
} else if (table == sharedWithMeTable) {
    if (column == 7) {
        action = ButtonAction.DOWNLOAD_SHARED;
    } else if (column == 8) {
        action = ButtonAction.UPLOAD_SHARED;
    } else {
        action = ButtonAction.HISTORY_SHARED;
    }
}

        isPushed = true;
        return button;
    }

    


          @Override
    public Object getCellEditorValue() {
        if (currentTable != null && isPushed) {
            if (action == ButtonAction.DOWNLOAD) {
                long fileId = (currentTable == fileListTable)
                    ? Long.parseLong(fileTableModel.getValueAt(currentRow, 4).toString())
                    : Long.parseLong(downloadTableModel.getValueAt(currentRow, 4).toString());
                downloadFileById(fileId);
            } else if (action == ButtonAction.HISTORY) {
                long fileId = Long.parseLong(fileTableModel.getValueAt(currentRow, 4).toString());
                showHistoryDialog(fileId);
            } else if (action == ButtonAction.DELETE) {
            long fileId = Long.parseLong(fileTableModel.getValueAt(currentRow, 4).toString());
            deleteFileById(fileId);
        } else if (action == ButtonAction.CANCEL_SHARE) {
                long shareId = Long.parseLong(myShareTableModel.getValueAt(currentRow, 0).toString());
                cancelShare(shareId);
            } else if (action == ButtonAction.DOWNLOAD_SHARED || action == ButtonAction.UPLOAD_SHARED
                    || action == ButtonAction.HISTORY_SHARED
                    || action == ButtonAction.DOWNLOAD_MY_SHARE || action == ButtonAction.UPLOAD_MY_SHARE) {
                if (currentTable == sharedWithMeTable && currentRow >= 0 && currentRow < sharedWithMeItems.size()) {
                    ShareItem shareItem = sharedWithMeItems.get(currentRow);
                    if (action == ButtonAction.DOWNLOAD_SHARED) {
                        downloadSharedFile(shareItem);
                    } else if (action == ButtonAction.UPLOAD_SHARED) {
                        handleSharedUpload(shareItem);
                    } else if (action == ButtonAction.HISTORY_SHARED) {
                        showHistoryDialog(shareItem.fileId);
                    }
                } else if (currentTable == myShareTable && currentRow >= 0 && currentRow < myShareItems.size()) {
                    ShareItem shareItem = myShareItems.get(currentRow);
                    if (action == ButtonAction.DOWNLOAD_MY_SHARE) {
                        downloadSharedFile(shareItem);
                    } else if (action == ButtonAction.UPLOAD_MY_SHARE) {
                        handleSharedUpload(shareItem);
                    } else if (action == ButtonAction.HISTORY_SHARED) {
                        showHistoryDialog(shareItem.fileId);
                    }
                }
            }
        }
        isPushed = false;
        currentTable = null;
        return button.getText();
    }

}
// 历史版本操作类型
private enum HistoryActionType {
    DOWNLOAD,
    ROLLBACK,
    DELETE
}

    class HistoryActionRenderer extends JButton implements TableCellRenderer {
    public HistoryActionRenderer() {
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        setText(value == null ? "" : value.toString());
        return this;
    }
}

    class HistoryActionEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
    private final JButton button = new JButton();
    private int editingRow = -1;
    private final HistoryActionType actionType;

    HistoryActionEditor(HistoryActionType actionType) {
        button.setOpaque(true);
        button.addActionListener(this);
        this.actionType = actionType;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                boolean isSelected, int row, int column) {
        button.setText(value == null ? "" : value.toString());
        editingRow = row;
        return button;
    }

    @Override
    public Object getCellEditorValue() {
        return button.getText();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        stopCellEditing();
        handleHistoryAction(editingRow, actionType);
    }
}





        /**
     * 主方法
     */
    public static void main(String[] args) {
        // 设置系统编码
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");
        
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            new CSClientGUI().setVisible(true);
        });
    }
}

