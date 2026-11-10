import os
import sys
import docx
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

def set_cell_background(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=140, bottom=140, left=180, right=180):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(f'''
        <w:tcMar {nsdecls("w")}>
            <w:top w:w="{top}" w:type="dxa"/>
            <w:bottom w:w="{bottom}" w:type="dxa"/>
            <w:left w:w="{left}" w:type="dxa"/>
            <w:right w:w="{right}" w:type="dxa"/>
        </w:tcMar>
    ''')
    tcPr.append(tcMar)

def set_table_borders(table, color="D0D7DE", sz="4", val="single"):
    tblPr = table._tbl.tblPr
    borders = parse_xml(f'''
        <w:tblBorders {nsdecls("w")}>
            <w:top w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>
            <w:bottom w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>
            <w:left w:val="none"/>
            <w:right w:val="none"/>
            <w:insideH w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>
            <w:insideV w:val="none"/>
        </w:tblBorders>
    ''')
    tblPr.append(borders)

def add_callout(doc, text, title="ARCHITECTURE NOTE", border_color="1B365D", bg_color="F4F6F9"):
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    
    cell = tbl.cell(0, 0)
    cell.width = Inches(6.8)
    set_cell_background(cell, bg_color)
    set_cell_margins(cell, top=140, bottom=140, left=220, right=200)
    
    # Left border only
    tcPr = cell._tc.get_or_add_tcPr()
    borders = parse_xml(f'''
        <w:tcBorders {nsdecls("w")}>
            <w:top w:val="none"/>
            <w:left w:val="single" w:sz="24" w:space="0" w:color="{border_color}"/>
            <w:bottom w:val="none"/>
            <w:right w:val="none"/>
        </w:tcBorders>
    ''')
    tcPr.append(borders)
    
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(4)
    run_t = p.add_run(f"[{title}] ")
    run_t.bold = True
    run_t.font.name = "Arial"
    run_t.font.size = Pt(9.5)
    run_t.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D) if border_color == "1B365D" else RGBColor(0xB7, 0x1C, 0x1C)
    
    run_b = p.add_run(text)
    run_b.font.name = "Arial"
    run_b.font.size = Pt(9.5)
    run_b.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    
    # Add an empty paragraph after table for spacing
    p_after = doc.add_paragraph()
    p_after.paragraph_format.space_before = Pt(0)
    p_after.paragraph_format.space_after = Pt(6)

def format_heading(p, text, level, font_size, color_rgb, space_before=12, space_after=6):
    p.paragraph_format.space_before = Pt(space_before)
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.keep_with_next = True
    run = p.add_run(text)
    run.font.name = "Arial"
    run.font.size = Pt(font_size)
    run.bold = True
    run.font.color.rgb = color_rgb

def main():
    doc = Document()
    
    # Page Setup: Standard Letter, 0.8 in margins (leaves 6.9 in printable width)
    sections = doc.sections
    for section in sections:
        section.top_margin = Inches(0.8)
        section.bottom_margin = Inches(0.8)
        section.left_margin = Inches(0.8)
        section.right_margin = Inches(0.8)
        section.page_width = Inches(8.5)
        section.page_height = Inches(11.0)
    
    # Header & Footer setup
    header = sections[0].header
    hp = header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    hrun = hp.add_run("CooBS Architecture Dossier | Group 2 Enterprise Capstone")
    hrun.font.name = "Arial"
    hrun.font.size = Pt(8.5)
    hrun.font.color.rgb = RGBColor(0x88, 0x88, 0x88)
    
    footer = sections[0].footer
    fp = footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    frun = fp.add_run("Confidential — For Architectural Review, Verification & Validation Only")
    frun.font.name = "Arial"
    frun.font.size = Pt(8.5)
    frun.font.color.rgb = RGBColor(0x88, 0x88, 0x88)
    
    # -------------------------------------------------------------
    # COVER / HEADER TITLE BLOCK
    # -------------------------------------------------------------
    p_pre = doc.add_paragraph()
    p_pre.paragraph_format.space_before = Pt(12)
    p_pre.paragraph_format.space_after = Pt(2)
    r_org = p_pre.add_run("COOPERATIVE BANKING SYSTEM (CooBS) — ENTERPRISE ARCHITECTURE")
    r_org.font.name = "Arial"
    r_org.font.size = Pt(10)
    r_org.bold = True
    r_org.font.color.rgb = RGBColor(0x00, 0x80, 0x80) # Teal accent
    
    p_title = doc.add_paragraph()
    p_title.paragraph_format.space_before = Pt(4)
    p_title.paragraph_format.space_after = Pt(6)
    r_title = p_title.add_run("Hybrid-Cloud Enterprise Architecture Specification & Validation Dossier")
    r_title.font.name = "Arial"
    r_title.font.size = Pt(22)
    r_title.bold = True
    r_title.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D) # Royal Navy
    
    p_sub = doc.add_paragraph()
    p_sub.paragraph_format.space_before = Pt(0)
    p_sub.paragraph_format.space_after = Pt(16)
    r_sub = p_sub.add_run("Comprehensive Architectural Blueprint: Azure AKS Microservices Platform, Hybrid IPsec/ExpressRoute Interconnect, and On-Premises Core Banking Ledger & Sovereignty Zone")
    r_sub.font.name = "Arial"
    r_sub.font.size = Pt(11)
    r_sub.italic = True
    r_sub.font.color.rgb = RGBColor(0x55, 0x55, 0x55)
    
    # Meta Details Table
    meta_table = doc.add_table(rows=5, cols=2)
    meta_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    meta_table.autofit = False
    set_table_borders(meta_table, color="B0BEC5", sz="4")
    
    meta_data = [
        ("Document Identifier", "CooBS-EA-V2.0-HYBRID-SPEC"),
        ("System Architecture Model", "Hybrid Multi-Zone: Azure Cloud (AKS) + On-Premises Core Datacenter"),
        ("Target Review Status", "Formal Evaluation, Architecture Defense & Stakeholder Validation"),
        ("Author / Engineering Group", "Group 2 Enterprise Architecture Team (Capstone Dev)"),
        ("Regulatory & Compliance Scope", "PCI DSS v4.0, GDPR Data Privacy, BSP Cooperative Banking Framework")
    ]
    
    col_widths = [Inches(2.2), Inches(4.6)]
    for r_idx, (k, v) in enumerate(meta_data):
        row = meta_table.rows[r_idx]
        for c_idx, text in enumerate((k, v)):
            cell = row.cells[c_idx]
            cell.width = col_widths[c_idx]
            set_cell_margins(cell, top=80, bottom=80, left=120, right=120)
            if c_idx == 0:
                set_cell_background(cell, "F0F4F8")
            else:
                set_cell_background(cell, "FFFFFF")
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(9.5)
            if c_idx == 0:
                run.bold = True
                run.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
            else:
                run.font.color.rgb = RGBColor(0x22, 0x22, 0x22)
                
    doc.add_paragraph().paragraph_format.space_after = Pt(12)
    
    # -------------------------------------------------------------
    # SECTION 1: EXECUTIVE ARCHITECTURAL SUMMARY
    # -------------------------------------------------------------
    format_heading(doc.add_paragraph(), "1. Executive Architectural Summary", 1, 15, RGBColor(0x1B, 0x36, 0x5D))
    
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.line_spacing = 1.15
    p.add_run(
        "The Cooperative Banking System (CooBS) architecture balances two mission-critical financial requirements: "
        "1) high-scale, omni-channel customer engagement with millisecond real-time transaction tracking, and "
        "2) strict data sovereignty, financial immutability, and zero-compromise regulatory compliance. "
        "To achieve this, CooBS implements a hybrid-cloud dual-zone topology. The public-facing ingress, identity tokens, "
        "transaction lifecycle orchestrator, fraud risk engine, and push notifications reside in Microsoft Azure (Azure Kubernetes Service - AKS). "
        "Conversely, customer master KYC, core savings/loan accounts, double-entry financial ledger balances, and Merkle-sealed audit stores "
        "are strictly isolated within an on-premises enterprise data center running Oracle Database 19c and specialized PostgreSQL audit nodes."
    )
    
    add_callout(
        doc,
        "Zero-Sovereignty Leakage Guarantee: Under no circumstances does plaintext KYC personal data (national IDs, residential addresses) "
        "or primary master balance ledgers reside permanently in cloud storage. Cloud compromises expose only short-lived session hashes "
        "and transient orchestrator tokens, neutralizing financial data exfiltration risk.",
        title="CORE ARCHITECTURAL INVARIANT"
    )
    
    # -------------------------------------------------------------
    # SECTION 2: HIGH-LEVEL ARCHITECTURE DIAGRAM & VISUAL MAP
    # -------------------------------------------------------------
    format_heading(doc.add_paragraph(), "2. Enterprise Architecture Blueprint", 1, 15, RGBColor(0x1B, 0x36, 0x5D))
    
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    p.add_run("The following architectural blueprint represents the complete hybrid infrastructure, microservice topology, interconnect channels, and data stores validated for production deployment:")
    
    img_path = os.path.abspath("docs/images/coobs_hybrid_architecture.png")
    if os.path.exists(img_path):
        p_img = doc.add_paragraph()
        p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_img.paragraph_format.space_before = Pt(6)
        p_img.paragraph_format.space_after = Pt(4)
        run_img = p_img.add_run()
        run_img.add_picture(img_path, width=Inches(6.8))
        
        p_cap = doc.add_paragraph()
        p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_cap.paragraph_format.space_before = Pt(0)
        p_cap.paragraph_format.space_after = Pt(12)
        r_cap = p_cap.add_run("Figure 1.0: CooBS Hybrid-Cloud Topology (Azure Cloud AKS + IPsec/ExpressRoute Gateway + On-Premises Core Banking)")
        r_cap.font.name = "Arial"
        r_cap.font.size = Pt(8.5)
        r_cap.italic = True
        r_cap.font.color.rgb = RGBColor(0x66, 0x66, 0x66)
    
    # Architectural Zones Table
    zone_table = doc.add_table(rows=5, cols=3)
    zone_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    zone_table.autofit = False
    set_table_borders(zone_table, color="D0D7DE")
    
    z_headers = ["Zone / Network Segment", "Hosting Environment", "Key Architectural Components & Services"]
    for idx, text in enumerate(z_headers):
        cell = zone_table.rows[0].cells[idx]
        set_cell_background(cell, "1B365D")
        set_cell_margins(cell, top=100, bottom=100, left=100, right=100)
        p = cell.paragraphs[0]
        run = p.add_run(text)
        run.bold = True
        run.font.name = "Arial"
        run.font.size = Pt(9)
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        
    z_data = [
        ("Zone 1: External Client & Ingress", "Public Internet & Partner VPNs", "Mobile App (Flutter), Corporate Web, Partner Bank APIs, Teller/Staff Web, Azure Front Door / Application Gateway WAF"),
        ("Zone 2: Cloud Microservices Platform", "Azure Cloud (AKS VNet)", "Spring Cloud Gateway (8080), Auth (8081), Customer (8089), Transaction (8090), Orchestration (8091), Risk (8088), Notification (8084)"),
        ("Zone 3: Secure Hybrid Interconnect", "Dedicated Encrypted Tunnel", "Azure ExpressRoute / IPsec Site-to-Site VPN, Mutual TLS (mTLS), Strict Network Security Group (NSG) Whitelisting"),
        ("Zone 4: On-Premises Core Banking", "Corporate On-Premises DC", "Account Service (8082), Ledger Service (8083), Audit Service (8085), Batch Service (8093), Oracle DB 19c, PostgreSQL Audit Store")
    ]
    
    z_widths = [Inches(1.8), Inches(1.8), Inches(3.2)]
    for r_idx, row_data in enumerate(z_data):
        row = zone_table.rows[r_idx + 1]
        for c_idx, text in enumerate(row_data):
            cell = row.cells[c_idx]
            cell.width = z_widths[c_idx]
            set_cell_margins(cell, top=80, bottom=80, left=100, right=100)
            if r_idx % 2 == 1:
                set_cell_background(cell, "F9FBFD")
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(8.5)
            if c_idx == 0:
                run.bold = True
                run.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
            else:
                run.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
                
    doc.add_paragraph().paragraph_format.space_after = Pt(12)
    
    # -------------------------------------------------------------
    # SECTION 3: MICROSERVICE BOUNDARIES & INVENTORY
    # -------------------------------------------------------------
    format_heading(doc.add_paragraph(), "3. Microservices Inventory & Responsibility Matrix", 1, 15, RGBColor(0x1B, 0x36, 0x5D))
    
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    p.add_run("The CooBS ecosystem is partitioned into 11 specialized microservices operating across cloud and on-premises zones with well-defined interface contracts:")
    
    svc_table = doc.add_table(rows=12, cols=5)
    svc_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    svc_table.autofit = False
    set_table_borders(svc_table, color="D0D7DE")
    
    svc_headers = ["Microservice", "Zone", "Port", "Datastore / Cache", "Core Responsibilities & Capabilities"]
    for idx, text in enumerate(svc_headers):
        cell = svc_table.rows[0].cells[idx]
        set_cell_background(cell, "1B365D")
        set_cell_margins(cell, top=100, bottom=100, left=80, right=80)
        p = cell.paragraphs[0]
        run = p.add_run(text)
        run.bold = True
        run.font.name = "Arial"
        run.font.size = Pt(8.5)
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        
    svc_data = [
        ("api-gateway", "Cloud (AKS)", "8080", "Redis (Rate Limiter)", "Reverse proxy, JWT token inspection, rate limiting, request correlation ID generation, CORS."),
        ("auth-service", "Cloud (AKS)", "8081", "identity_db, Redis", "Customer/Staff login, MFA TOTP challenge verification, RSA256 JWT minting, Redis token blacklist."),
        ("customer-service", "Cloud (AKS)", "8089", "customer_db, Redis", "Customer 360 profile aggregator, channel preferences, beneficiary management, temporary KYC cache."),
        ("transaction-service", "Cloud (AKS)", "8090", "transaction_db", "Transfer ingress, 1-3 stage timeline emitter, idempotent intake, partner bank webhooks, DLQ replay."),
        ("orchestration-service", "Cloud (AKS)", "8091", "orchestration_db", "Saga orchestrator (O1), coordinates stages 4-11, risk evaluation, hold placement, capture compensation."),
        ("risk-engine", "Cloud (AKS)", "8088", "Stateless / Models", "Real-time fraud scoring (0.00-1.00), velocity checks, tier limits, fail-closed risk decision engine."),
        ("notification-service", "Cloud (AKS)", "8084", "notification_db", "Async event consumer, multi-channel dispatch (In-App SSE/WebSockets, SMS, Push, Email)."),
        ("account-service", "On-Premises", "8082", "Oracle 19c (KYC/ACCT)", "Master KYC repository, account creation, account flag/hold status, account closure workflow."),
        ("ledger-service", "On-Premises", "8083", "Oracle 19c, Redis", "Double-entry accounting, balance invariant enforcement (B >= 0), pessimistic locks, Outbox pattern."),
        ("audit-service", "On-Premises", "8085", "Postgres (audit_store)", "Forensic audit logging, Merkle tree cryptographic verification, compliance reporting, fraud investigation."),
        ("batch-service", "On-Premises", "8093", "Oracle 19c", "Automated End-of-Day (EOD) pipeline, interest accrual, loan amortization, GL debit/credit balancing.")
    ]
    
    s_widths = [Inches(1.5), Inches(1.0), Inches(0.6), Inches(1.5), Inches(2.2)]
    for r_idx, row_data in enumerate(svc_data):
        row = svc_table.rows[r_idx + 1]
        for c_idx, text in enumerate(row_data):
            cell = row.cells[c_idx]
            cell.width = s_widths[c_idx]
            set_cell_margins(cell, top=60, bottom=60, left=80, right=80)
            if r_idx % 2 == 1:
                set_cell_background(cell, "F9FBFD")
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(8.0)
            if c_idx == 0:
                run.bold = True
                run.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
            elif c_idx == 1:
                run.bold = True
                run.font.color.rgb = RGBColor(0x00, 0x80, 0x80) if "Cloud" in text else RGBColor(0x2E, 0x7D, 0x32)
            else:
                run.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
                
    doc.add_paragraph().paragraph_format.space_after = Pt(12)
    
    # -------------------------------------------------------------
    # SECTION 4: REAL-TIME TRANSACTION MONITORING & 11-STAGE SAGA
    # -------------------------------------------------------------
    format_heading(doc.add_paragraph(), "4. Real-Time Transaction Monitoring & 11-Stage Saga Validation", 1, 15, RGBColor(0x1B, 0x36, 0x5D))
    
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    p.add_run(
        "A cornerstone feature of CooBS is the Real-Time Transaction Monitoring Dashboard. "
        "Transactions pass through eleven rigorous stages managed by the Saga Orchestrator. "
        "Clients poll the endpoint GET /api/v1/transactions/{id}/timeline at 1-second intervals. "
        "The system enforces strict role-based visibility to prevent fraud reconnaissance while empowering bank staff."
    )
    
    # Role Visibility Table
    rv_table = doc.add_table(rows=6, cols=3)
    rv_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    rv_table.autofit = False
    set_table_borders(rv_table, color="D0D7DE")
    
    rv_headers = ["Validation Dimension", "Customer / Member View", "Teller / Admin View"]
    for idx, text in enumerate(rv_headers):
        cell = rv_table.rows[0].cells[idx]
        set_cell_background(cell, "1B365D")
        set_cell_margins(cell, top=100, bottom=100, left=100, right=100)
        p = cell.paragraphs[0]
        run = p.add_run(text)
        run.bold = True
        run.font.name = "Arial"
        run.font.size = Pt(8.5)
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        
    rv_data = [
        ("Visible Stepper Stages", "3 High-Level Stages:\n1. INITIATED\n2. PROCESSING\n3. POSTED (or FAILED)", "Full 11 Granular Stages:\nStages 1 through 11 fully rendered in live sequence."),
        ("Stage Mapping Logic", "Stage 1 = INITIATED\nStages 2-7 = PROCESSING\nStage 8 onward = POSTED", "1:1 direct mapping with internal saga execution events and timestamps."),
        ("Risk & Fraud Details", "Completely Redacted (Internal risk scores, rule thresholds, and hold IDs are never exposed).", "Full Diagnostic Visibility (Exposes risk score e.g., 'score 0.15', rule hits, holdId e.g. 9821)."),
        ("Failure Messaging", "Customer-Friendly Sentence in failureMessage:\n'Transfer could not be completed due to insufficient balance.'", "Machine Diagnostic in failureCode:\nEXACT code: LIMIT_EXCEEDED, RISK_DECLINED, INSUFFICIENT_FUNDS."),
        ("API Response Flag", "\"view\": \"CUSTOMER\"", "\"view\": \"STAFF\"")
    ]
    
    rv_widths = [Inches(1.8), Inches(2.5), Inches(2.5)]
    for r_idx, row_data in enumerate(rv_data):
        row = rv_table.rows[r_idx + 1]
        for c_idx, text in enumerate(row_data):
            cell = row.cells[c_idx]
            cell.width = rv_widths[c_idx]
            set_cell_margins(cell, top=80, bottom=80, left=100, right=100)
            if r_idx % 2 == 1:
                set_cell_background(cell, "F9FBFD")
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(8.5)
            if c_idx == 0:
                run.bold = True
                run.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
            else:
                run.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
                
    doc.add_paragraph().paragraph_format.space_after = Pt(8)
    
    # 11 Stages Table
    stage_table = doc.add_table(rows=12, cols=4)
    stage_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    stage_table.autofit = False
    set_table_borders(stage_table, color="D0D7DE")
    
    st_headers = ["Stage #", "Stage Name", "Emitted By Service", "Operational Verification & Criteria"]
    for idx, text in enumerate(st_headers):
        cell = stage_table.rows[0].cells[idx]
        set_cell_background(cell, "008080")
        set_cell_margins(cell, top=100, bottom=100, left=80, right=80)
        p = cell.paragraphs[0]
        run = p.add_run(text)
        run.bold = True
        run.font.name = "Arial"
        run.font.size = Pt(8.5)
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        
    st_data = [
        ("1", "INITIATED", "transaction-service", "Ingress endpoint accepts transfer payload, generates UUID & TX reference number."),
        ("2", "VALIDATED", "transaction-service", "Payload validated (positive amount, valid currency PHP, account status active)."),
        ("3", "AUTHENTICATED", "transaction-service", "JWT validated; verifies caller owns source account or is authorized bank teller."),
        ("4", "FRAUD_CHECK", "orchestration-service", "Risk Engine scores transaction. Scores > 0.60 or timeouts trigger fail-closed decline."),
        ("5", "LIMIT_CHECK", "orchestration-service", "Evaluates customer daily transfer limits and KYC tier thresholds."),
        ("6", "FUNDS_CHECK", "orchestration-service", "Calls ledger-service /holds. Places debit hold and verifies available balance (B >= 0)."),
        ("7", "AUTHORIZED", "orchestration-service", "Debit hold confirmed (RESERVED). Transfer officially authorized to post."),
        ("8", "POSTED", "orchestration-service", "Calls ledger-service /capture. Money is permanently posted to double-entry ledger."),
        ("9", "LEDGER_UPDATE", "orchestration-service", "Confirmed via Kafka event (ledger.transfer.completed.v1) from ledger Outbox."),
        ("10", "NOTIFICATION", "orchestration-service", "Confirmed via Kafka event (notification.dispatched.v1) after customer alert sent."),
        ("11", "RECONCILIATION", "orchestration-service", "Transaction audit trail sealed with cryptographic Merkle proof; saga COMPLETED.")
    ]
    
    st_widths = [Inches(0.6), Inches(1.5), Inches(1.6), Inches(3.1)]
    for r_idx, row_data in enumerate(st_data):
        row = stage_table.rows[r_idx + 1]
        for c_idx, text in enumerate(row_data):
            cell = row.cells[c_idx]
            cell.width = st_widths[c_idx]
            set_cell_margins(cell, top=60, bottom=60, left=80, right=80)
            if r_idx % 2 == 1:
                set_cell_background(cell, "F9FBFD")
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(8.0)
            if c_idx == 0:
                run.bold = True
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            elif c_idx == 1:
                run.bold = True
                run.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
            else:
                run.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
                
    doc.add_paragraph().paragraph_format.space_after = Pt(12)
    
    # -------------------------------------------------------------
    # SECTION 5: SECURITY, DATA SOVEREIGNTY & RESILIENCE
    # -------------------------------------------------------------
    format_heading(doc.add_paragraph(), "5. Security, Data Sovereignty & Resilience Architecture", 1, 15, RGBColor(0x1B, 0x36, 0x5D))
    
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    p.add_run(
        "Banking architecture demands multi-layer defense in depth. CooBS implements zero-trust principles across "
        "perimeter boundaries, inter-service communications, datastore encryption, and asynchronous retry mechanics:"
    )
    
    sec_points = [
        ("Perimeter Defense & Rate Limiting: ", "Spring Cloud Gateway enforces Redis-backed token bucket rate limits per IP and user ID, preventing credential brute-forcing and API denial of service."),
        ("Zero-Trust Service-to-Service Security: ", "All internal API communication across Kubernetes pods and hybrid tunnels requires signed RS256 JWT service tokens with strict ROLE_SERVICE authorization."),
        ("Immediate Token Revocation: ", "Logout, password resets, and session terminations publish to Redis Token Blacklist, instantly invalidating active JWTs across the cluster."),
        ("Transactional Outbox Pattern: ", "ledger-service guarantees zero message loss by persisting events to an Oracle OUTBOX table in the same ACID transaction as the ledger mutation, asynchronously relayed to Kafka."),
        ("Fail-Closed Risk & Idempotent Retries: ", "Downstream ledger calls use UUID-keyed idempotency. Capture failures trigger automated compensation (release hold) rather than dangling state."),
        ("Dead-Letter Queue (DLQ) & Admin Replay: ", "Unresolvable Kafka consumers retry with exponential backoff before landing in .DLT dead-letter topics with full administrative replay capabilities.")
    ]
    
    for title, desc in sec_points:
        p_pt = doc.add_paragraph()
        p_pt.paragraph_format.space_before = Pt(2)
        p_pt.paragraph_format.space_after = Pt(4)
        p_pt.paragraph_format.left_indent = Inches(0.25)
        
        r_bullet = p_pt.add_run("• ")
        r_bullet.bold = True
        r_bullet.font.color.rgb = RGBColor(0x00, 0x80, 0x80)
        
        r_t = p_pt.add_run(title)
        r_t.bold = True
        r_t.font.name = "Arial"
        r_t.font.size = Pt(9.5)
        r_t.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
        
        r_d = p_pt.add_run(desc)
        r_d.font.name = "Arial"
        r_d.font.size = Pt(9.5)
        r_d.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
        
    doc.add_paragraph().paragraph_format.space_after = Pt(12)
    
    # -------------------------------------------------------------
    # SECTION 6: ARCHITECTURAL VALIDATION & SIGN-OFF CHECKLIST
    # -------------------------------------------------------------
    format_heading(doc.add_paragraph(), "6. Architectural Validation & Sign-Off Scorecard", 1, 15, RGBColor(0x1B, 0x36, 0x5D))
    
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    p.add_run("This checklist serves as the formal instrument for validation by enterprise reviewers, technical panels, and security evaluators:")
    
    chk_table = doc.add_table(rows=8, cols=4)
    chk_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    chk_table.autofit = False
    set_table_borders(chk_table, color="D0D7DE")
    
    chk_headers = ["Review Criterion", "Design Verification Requirement", "Artifact / Evidence Location", "Result"]
    for idx, text in enumerate(chk_headers):
        cell = chk_table.rows[0].cells[idx]
        set_cell_background(cell, "1B365D")
        set_cell_margins(cell, top=100, bottom=100, left=80, right=80)
        p = cell.paragraphs[0]
        run = p.add_run(text)
        run.bold = True
        run.font.name = "Arial"
        run.font.size = Pt(8.5)
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        
    chk_data = [
        ("Separation of Concerns", "Cloud layer hosts engagement & saga; On-prem hosts ledger & KYC master.", "Figure 1.0; Section 3 Matrix", "[ PASS ]"),
        ("Data Sovereignty", "Customer PII & core balances are strictly on-premises; never in cloud DBs.", "Oracle 19c Schema; Section 1 Note", "[ PASS ]"),
        ("Dual-Write Mitigation", "Postings use Outbox table + Kafka relay to guarantee at-least-once delivery.", "LedgerOutboxRelay.java; Section 5", "[ PASS ]"),
        ("Saga Resilience", "Saga failure releases holds via compensating transaction (releaseHold).", "TransferSaga.java; Section 4", "[ PASS ]"),
        ("Role Security & Redaction", "Customer view redacts fraud scores/details; Staff view exposes all 11 stages.", "FUTURE_INTEGRATED_API_CONTRACT.md", "[ PASS ]"),
        ("Real-Time Timeline", "Timeline endpoint polled at 1s intervals with idempotent duplicate suppression.", "TimelineController.java; Section 4", "[ PASS ]"),
        ("Zero-Trust Interconnect", "All hybrid traffic crosses IPsec/ExpressRoute with mTLS & Key Vault certs.", "Azure Key Vault; Section 2 Legend", "[ PASS ]")
    ]
    
    c_widths = [Inches(1.6), Inches(2.5), Inches(1.8), Inches(0.9)]
    for r_idx, row_data in enumerate(chk_data):
        row = chk_table.rows[r_idx + 1]
        for c_idx, text in enumerate(row_data):
            cell = row.cells[c_idx]
            cell.width = c_widths[c_idx]
            set_cell_margins(cell, top=70, bottom=70, left=80, right=80)
            if r_idx % 2 == 1:
                set_cell_background(cell, "F9FBFD")
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            run = p.add_run(text)
            run.font.name = "Arial"
            run.font.size = Pt(8.0)
            if c_idx == 0:
                run.bold = True
                run.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
            elif c_idx == 3:
                run.bold = True
                run.font.color.rgb = RGBColor(0x2E, 0x7D, 0x32)
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            else:
                run.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
                
    doc.add_paragraph().paragraph_format.space_after = Pt(16)
    
    # Formal Sign-Off Signature Block
    sign_table = doc.add_table(rows=2, cols=3)
    sign_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    sign_table.autofit = False
    set_table_borders(sign_table, color="D0D7DE")
    
    sign_headers = ["Enterprise Architecture Lead", "Chief Information Security Officer", "Lead Academic / Industry Reviewer"]
    for idx, text in enumerate(sign_headers):
        cell = sign_table.rows[0].cells[idx]
        set_cell_background(cell, "F0F4F8")
        set_cell_margins(cell, top=80, bottom=80, left=100, right=100)
        p = cell.paragraphs[0]
        run = p.add_run(text)
        run.bold = True
        run.font.name = "Arial"
        run.font.size = Pt(8.5)
        run.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
        
    s_col_w = Inches(2.26)
    for c_idx in range(3):
        cell = sign_table.rows[1].cells[c_idx]
        cell.width = s_col_w
        set_cell_margins(cell, top=200, bottom=100, left=100, right=100)
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(12)
        p.paragraph_format.space_after = Pt(2)
        run = p.add_run("Signature: ___________________\nDate:       ____ / ____ / 2026")
        run.font.name = "Arial"
        run.font.size = Pt(8.5)
        run.font.color.rgb = RGBColor(0x66, 0x66, 0x66)

    # Save Document
    out_dir = os.path.abspath("docs")
    os.makedirs(out_dir, exist_ok=True)
    out_file = os.path.join(out_dir, "CooBS_Hybrid_Enterprise_Architecture_Dossier.docx")
    doc.save(out_file)
    print(f"Successfully generated Word document: {out_file}")

if __name__ == "__main__":
    main()
