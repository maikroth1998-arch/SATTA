require('dotenv').config();

const express = require('express');
const cors = require('cors');
const XLSX = require('xlsx');
const path = require('path');
const fs = require('fs');

//For worklogs
const ExcelJS = require('exceljs');
const { ChartJSNodeCanvas } = require('chartjs-node-canvas');

const app = express();
const PORT = process.env.PORT || 3000;
const DB_FILE = process.env.DB_FILE || path.join(__dirname, 'service_contracts_app.xlsx');

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

function ensureDbExists() {
  if (fs.existsSync(DB_FILE)) return;

  const workbook = XLSX.utils.book_new();

  const contractsSheet = XLSX.utils.json_to_sheet([]);
  const worklogsSheet = XLSX.utils.json_to_sheet([
    {
      id: '',
      order_number: '',
      customer: '',
      ticket_number: '',
      booked_at: '',
      person: '',
      hours: '',
      comment: ''
    }
  ]);

  XLSX.utils.book_append_sheet(workbook, contractsSheet, 'contracts');
  XLSX.utils.book_append_sheet(workbook, worklogsSheet, 'worklogs');
  XLSX.writeFile(workbook, DB_FILE);
}

function readWorkbook() {
  ensureDbExists();
  return XLSX.readFile(DB_FILE);
}

function writeWorkbook(workbook) {
  XLSX.writeFile(workbook, DB_FILE);
}

function getSheetRows(workbook, sheetName) {
  const sheet = workbook.Sheets[sheetName];
  if (!sheet) return [];
  return XLSX.utils.sheet_to_json(sheet, { defval: '' });
}

function setSheetRows(workbook, sheetName, rows) {
  const sheet = XLSX.utils.json_to_sheet(rows);
  workbook.Sheets[sheetName] = sheet;

  if (!workbook.SheetNames.includes(sheetName)) {
    workbook.SheetNames.push(sheetName);
  }
}

function normalizeString(value) {
  return String(value ?? '').trim();
}

function parseNumber(value) {
  if (value === null || value === undefined) return 0;
  const str = String(value).trim().replace(',', '.');
  const num = parseFloat(str);
  return Number.isNaN(num) ? 0 : num;
}

function roundQuarter(value) {
  return Math.round(value * 4) / 4;
}

function getWorklogsForOrder(worklogs, orderNumber) {
  return worklogs.filter(
    row => normalizeString(row.order_number) === normalizeString(orderNumber)
  );
}

function getWorklogsForTicket(worklogs, orderNumber, ticketNumber) {
  return worklogs.filter(
    row =>
      normalizeString(row.order_number) === normalizeString(orderNumber) &&
      normalizeString(row.ticket_number) === normalizeString(ticketNumber)
  );
}

function getBookedHoursForTicket(worklogs, orderNumber, ticketNumber) {
  return roundQuarter(
    getWorklogsForTicket(worklogs, orderNumber, ticketNumber)
      .reduce((sum, row) => sum + parseNumber(row.hours), 0)
  );
}

function aggregateHoursByPersonForTicket(worklogs, orderNumber, ticketNumber) {
  const map = new Map();

  for (const row of worklogs) {
    if (normalizeString(row.order_number) !== normalizeString(orderNumber)) continue;
    if (normalizeString(row.ticket_number) !== normalizeString(ticketNumber)) continue;

    const person = normalizeString(row.person);
    const hours = parseNumber(row.hours);

    if (!person) continue;

    map.set(person, roundQuarter((map.get(person) || 0) + hours));
  }

  return Array.from(map.entries()).map(([person, hours]) => ({
    person,
    hours
  }));
}

function getTicketWorklogDetails(worklogs, orderNumber, ticketNumber) {
  return worklogs
    .filter(
      row =>
        normalizeString(row.order_number) === normalizeString(orderNumber) &&
        normalizeString(row.ticket_number) === normalizeString(ticketNumber)
    )
    .map(row => ({
      id: row.id,
      customer: normalizeString(row.customer),
      person: normalizeString(row.person),
      hours: parseNumber(row.hours),
      booked_at: normalizeString(row.booked_at),
      comment: normalizeString(row.comment)
    }))
    .sort((a, b) => String(b.booked_at).localeCompare(String(a.booked_at)));
}

function getNextWorklogId(worklogs) {
  const ids = worklogs
    .map(row => parseInt(row.id, 10))
    .filter(n => !Number.isNaN(n));

  if (ids.length === 0) return 1;
  return Math.max(...ids) + 1;
}

function requireExportToken(req, res, next) {
  const expectedToken = String(process.env.EXPORT_TOKEN || '').trim();
  const token = String(req.headers['x-export-token'] || '').trim();

  if (!expectedToken) {
    return res.status(500).json({ error: 'EXPORT_TOKEN is not configured' });
  }

  if (token !== expectedToken) {
    return res.status(401).json({ error: 'Unauthorized' });
  }

  next();
}


////////////////////////////////////////////////////////////////////////
// Helperfunctions for import contracts
function normalizeImportString(value) {
  if (value === null || value === undefined) return '';

  if (typeof value === 'number') {
    return String(Math.trunc(value));
  }

  return String(value).trim();
}

function parseFlexibleNumber(value) {
  if (value === null || value === undefined) return 0;

  let str = String(value).trim();
  if (!str) return 0;

  const lower = str.toLowerCase();
  if (lower === 'unlimited') return 0;

  str = str.replace(/\s+/g, '');

  if (str.includes(',') && str.includes('.')) {
    str = str.replace(/\./g, '').replace(',', '.');
  } else if (str.includes(',')) {
    str = str.replace(',', '.');
  }

  const match = str.match(/-?\d+(?:\.\d+)?/);
  if (!match) return 0;

  const num = parseFloat(match[0]);
  return Number.isNaN(num) ? 0 : num;
}

function extractSoldHours(hoursSoldField) {
  return parseFlexibleNumber(hoursSoldField);
}

function extractUsedHours(hoursUsedField) {
  return parseFlexibleNumber(hoursUsedField);
}

function hasNumericSoldHours(row) {
  const soldHours = extractSoldHours(row['Contract hours sold']);
  return soldHours > 0;
}

function formatExcelMonthYear(value) {
  if (value === null || value === undefined || value === '') {
    return '';
  }

  const tryFormatExcelSerial = (serial) => {
    const date = XLSX.SSF.parse_date_code(serial);
    if (!date) return '';

    const jsDate = new Date(date.y, date.m - 1, date.d || 1);
    return jsDate.toLocaleString('en-US', {
      month: 'short',
      year: 'numeric'
    });
  };

  if (typeof value === 'number' && Number.isFinite(value)) {
    const formatted = tryFormatExcelSerial(value);
    if (formatted) return formatted;
    return String(value);
  }

  const str = String(value).trim();
  if (!str) return '';

  // Handle Excel serial date passed as string, e.g. "44713"
  if (/^\d+(\.\d+)?$/.test(str)) {
    const serial = Number(str);
    if (Number.isFinite(serial)) {
      const formatted = tryFormatExcelSerial(serial);
      if (formatted) return formatted;
    }
  }

  const parsed = new Date(str);
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString('en-US', {
      month: 'short',
      year: 'numeric'
    });
  }

  return str;
}

function formatToMonthEndIso(value) {
  if (value === null || value === undefined || value === '') {
    return '';
  }

  if (typeof value === 'number') {
    const date = XLSX.SSF.parse_date_code(value);
    if (!date) return '';

    const lastDay = new Date(date.y, date.m, 0); // day 0 of next month = last day of current month
    return lastDay.toISOString().slice(0, 10);
  }

  const str = String(value).trim();
  if (!str) return '';

  const parsed = new Date(str);
  if (!Number.isNaN(parsed.getTime())) {
    const lastDay = new Date(parsed.getFullYear(), parsed.getMonth() + 1, 0);
    return lastDay.toISOString().slice(0, 10);
  }

  return '';
}

function getCombinedUsedHoursTotal(contract, worklogs, orderNumber) {
  const sourceUsedHours = parseNumber(contract.used_hours);
  const appHoursAfterLastImport = getUnsyncedUsedHoursTotalForOrder(
    worklogs,
    orderNumber,
    contract.import_synced_until
  );

  return roundQuarter(sourceUsedHours + appHoursAfterLastImport);
}
function isAfterSyncCutoff(row, syncCutoff) {
  const cutoff = normalizeString(syncCutoff);
  if (!cutoff) return true;

  const bookedAt = normalizeString(row.booked_at);
  if (!bookedAt) return true;

  const bookedDate = new Date(bookedAt);
  const cutoffDate = new Date(cutoff);

  if (Number.isNaN(bookedDate.getTime()) || Number.isNaN(cutoffDate.getTime())) {
    return true;
  }

  return bookedDate > cutoffDate;
}

function getUnsyncedUsedHoursTotalForOrder(worklogs, orderNumber, syncCutoff) {
  return roundQuarter(
    getWorklogsForOrder(worklogs, orderNumber)
      .filter(row => isAfterSyncCutoff(row, syncCutoff))
      .reduce((sum, row) => sum + parseNumber(row.hours), 0)
  );
}
function transformContractRows(rows, existingMap = new Map(), importTime = new Date().toISOString()) {
  const contracts = [];

  for (const row of rows) {
    const orderNumber = normalizeImportString(row['Order no.']);
    if (!orderNumber) continue;

    const soldHours = extractSoldHours(row['Contract hours sold']);
    const usedHours = extractUsedHours(row['Contract hours used']);

    const existingContract = existingMap.get(normalizeString(orderNumber));
    const previousUsedHours = parseNumber(existingContract?.used_hours);
    const previousSyncCutoff = normalizeString(existingContract?.import_synced_until);

    let importSyncedUntil = previousSyncCutoff;

    // Important:
    // Only move the sync cutoff forward when Excel/SAP used_hours increased.
    if (!existingContract || usedHours > previousUsedHours) {
      importSyncedUntil = importTime;
    }

    contracts.push({
      order_number: orderNumber,
      external_id: normalizeImportString(row['External ID']),
      customer: normalizeImportString(row['Customer']),
      country: normalizeImportString(row['Country']),
      start: formatExcelMonthYear(row['Start']),
      valid_until: formatExcelMonthYear(row['valid until']),
      machine_type: normalizeImportString(row['Maschine type']),
      machine_no: normalizeImportString(row['Maschine no.']),
      contract_type: normalizeImportString(row['Contract type']),
      sold_hours: soldHours,
      used_hours: usedHours,
      import_synced_until: importSyncedUntil,
      comment: normalizeImportString(row['Comment / additional information']),
      contract_owner: normalizeImportString(row['Contract owner']),
      processed_by: normalizeImportString(row['Processed by'])
    });
  }

  const uniqueMap = new Map();

  for (const contract of contracts) {
    uniqueMap.set(contract.order_number, contract);
  }

  return Array.from(uniqueMap.values());
}

function splitExternalIds(value) {
  return String(value ?? '')
    .split(',')
    .map(id => id.trim())
    .filter(Boolean);
}

function findContractByExternalId(contracts, externalId) {
  const normalizedExternalId = normalizeString(externalId);
  if (!normalizedExternalId) return null;

  return contracts.find(row => {
    const externalIds = splitExternalIds(row.external_id);
    return externalIds.some(id => normalizeString(id) === normalizedExternalId);
  });
}

function findContractsByExternalId(contracts, externalId) {
  const normalizedExternalId = normalizeString(externalId);
  if (!normalizedExternalId) return [];

  return contracts.filter(row => {
    const externalIds = splitExternalIds(row.external_id);
    return externalIds.some(id => normalizeString(id) === normalizedExternalId);
  });
}

// Bearer protection 

function requireAdminToken(req, res, next) {
  const expectedToken = process.env.ADMIN_IMPORT_TOKEN;

  if (!expectedToken) {
    return res.status(500).json({
      error: 'ADMIN_IMPORT_TOKEN is not configured'
    });
  }

  const authHeader = req.headers.authorization || '';
  const [scheme, token] = authHeader.split(' ');

  if (scheme !== 'Bearer' || token !== expectedToken) {
    return res.status(401).json({ error: 'Unauthorized' });
  }

  next();
}

// export worklogs
app.get('/export/worklogs', requireExportToken, async (req, res) => {
  try {
    const sourceWorkbook = readWorkbook();

    const worklogs = getSheetRows(sourceWorkbook, 'worklogs').filter(
      row => normalizeString(row.id) || normalizeString(row.order_number)
    );

    const exportRows = worklogs.map(row => ({
      ID: normalizeString(row.id),
      'Order Number': normalizeString(row.order_number),
      Customer: normalizeString(row.customer),
      'Ticket Number': normalizeString(row.ticket_number),
      'Booked At': normalizeString(row.booked_at),
      Person: normalizeString(row.person),
      Hours: parseNumber(row.hours),
      Comment: normalizeString(row.comment)
    }));

    const workbook = new ExcelJS.Workbook();
    workbook.creator = 'ServiceAgreement Time Tracker App';
    workbook.created = new Date();

    const worksheet = workbook.addWorksheet('Worklogs');

    worksheet.columns = [
      { header: 'ID', key: 'ID', width: 10 },
      { header: 'Order Number', key: 'Order Number', width: 18 },
      { header: 'Customer', key: 'Customer', width: 28 },
      { header: 'Ticket Number', key: 'Ticket Number', width: 16 },
      { header: 'Booked At', key: 'Booked At', width: 24 },
      { header: 'Person', key: 'Person', width: 22 },
      { header: 'Hours', key: 'Hours', width: 10 },
      { header: 'Comment', key: 'Comment', width: 40 }
    ];

    worksheet.addRows(exportRows);

    worksheet.addTable({
      name: 'WorklogsTable',
      ref: 'A1',
      headerRow: true,
      totalsRow: true,
      style: {
        theme: 'TableStyleMedium2',
        showRowStripes: true
      },
      columns: [
        { name: 'ID', filterButton: true },
        { name: 'Order Number', filterButton: true },
        { name: 'Customer', filterButton: true },
        { name: 'Ticket Number', filterButton: true },
        { name: 'Booked At', filterButton: true },
        { name: 'Person', filterButton: true },
        { name: 'Hours', filterButton: true },
        { name: 'Comment', filterButton: true }
      ],
      rows: exportRows.map(row => [
        row.ID,
        row['Order Number'],
        row.Customer,
        row['Ticket Number'],
        row['Booked At'],
        row.Person,
        row.Hours,
        row.Comment
      ])
    });

    worksheet.getRow(1).font = { bold: true };
    worksheet.views = [{ state: 'frozen', ySplit: 1 }];

    const summaryMap = new Map();

    for (const row of exportRows) {
      const customer = row.Customer || 'Unknown customer';
      const person = row.Person || 'Unknown person';
      const hours = Number(row.Hours) || 0;

      if (!summaryMap.has(customer)) {
        summaryMap.set(customer, new Map());
      }

      const personMap = summaryMap.get(customer);
      personMap.set(person, (personMap.get(person) || 0) + hours);
    }

    const customers = Array.from(summaryMap.keys());
    const persons = Array.from(
      new Set(exportRows.map(row => row.Person || 'Unknown person'))
    );

    const summarySheet = workbook.addWorksheet('Summary');

    summarySheet.addRow(['Customer', ...persons, 'Total Hours']);

    customers.forEach(customer => {
      const personMap = summaryMap.get(customer);
      const rowValues = persons.map(person => personMap.get(person) || 0);
      const total = rowValues.reduce((sum, value) => sum + value, 0);

      summarySheet.addRow([customer, ...rowValues, total]);
    });

    summarySheet.getRow(1).font = { bold: true };
    summarySheet.views = [{ state: 'frozen', ySplit: 1 }];

    summarySheet.columns = [
      { width: 30 },
      ...persons.map(() => ({ width: 16 })),
      { width: 16 }
    ];

    if (customers.length > 0 && persons.length > 0) {
      const chartJSNodeCanvas = new ChartJSNodeCanvas({
        width: 1100,
        height: 600,
        backgroundColour: 'white'
      });

      const chartBuffer = await chartJSNodeCanvas.renderToBuffer({
        type: 'bar',
        data: {
          labels: customers,
          datasets: persons.map(person => ({
            label: person,
            data: customers.map(customer => {
              const personMap = summaryMap.get(customer);
              return personMap.get(person) || 0;
            })
          }))
        },
        options: {
          responsive: false,
          plugins: {
            title: {
              display: true,
              text: 'Used Hours by Customer and Person'
            },
            legend: {
              position: 'bottom'
            }
          },
          scales: {
            x: {
              stacked: true
            },
            y: {
              stacked: true,
              beginAtZero: true,
              title: {
                display: true,
                text: 'Hours'
              }
            }
          }
        }
      });

      const imageId = workbook.addImage({
        buffer: chartBuffer,
        extension: 'png'
      });

      const chartSheet = workbook.addWorksheet('Chart');
      chartSheet.addImage(imageId, {
        tl: { col: 0, row: 0 },
        ext: { width: 1100, height: 600 }
      });
    }

    const buffer = await workbook.xlsx.writeBuffer();

    const fileName = `worklogs_export_${new Date().toISOString().slice(0, 10)}.xlsx`;

    res.setHeader('Content-Type', 'text/plain');
    res.setHeader('X-File-Name', fileName);

    res.send(Buffer.from(buffer).toString('base64'));
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: 'Failed to export worklogs' });
  }
});


// import contracts endpoint
app.post('/admin/import-contracts', requireAdminToken, (req, res) => {
  try {
    const rows = req.body?.contracts;

    if (!Array.isArray(rows)) {
      return res.status(400).json({
        error: 'contracts must be an array'
      });
    }

    const importTime = normalizeString(req.body?.synced_until) || new Date().toISOString();
    const workbook = readWorkbook();
    const existingContracts = getSheetRows(workbook, 'contracts');
    const existingWorklogs = getSheetRows(workbook, 'worklogs');

    const existingMap = new Map(
      existingContracts.map(c => [normalizeString(c.order_number), c])
    );

    const transformedContracts = transformContractRows(rows, existingMap, importTime);

    const newMap = new Map(
      transformedContracts.map(c => [normalizeString(c.order_number), c])
    );

    // Identify changes
    const added = [];
    const removed = [];
    const changed = [];

    // Find added and changed contracts
    for (const [orderNumber, newContract] of newMap) {
      if (!existingMap.has(orderNumber)) {
        added.push(orderNumber);
      } else {
        const existingContract = existingMap.get(orderNumber);
        // Check if any field changed
        let hasChanges = false;
        for (const key in newContract) {
          if (String(newContract[key]) !== String(existingContract[key])) {
            hasChanges = true;
            break;
          }
        }
        if (hasChanges) {
          changed.push(orderNumber);
        }
      }
    }

    // Find removed contracts
    for (const orderNumber of existingMap.keys()) {
      if (!newMap.has(orderNumber)) {
        removed.push(orderNumber);
      }
    }

    // Log summary
    console.log('=== CONTRACT IMPORT SUMMARY ===');
    console.log(`Total imported: ${transformedContracts.length}`);
    console.log(`Added: ${added.length} ${added.length > 0 ? `[${added.join(', ')}]` : ''}`);
    console.log(`Changed: ${changed.length} ${changed.length > 0 ? `[${changed.join(', ')}]` : ''}`);
    console.log(`Removed: ${removed.length} ${removed.length > 0 ? `[${removed.join(', ')}]` : ''}`);
    console.log('==============================\n');

    setSheetRows(workbook, 'contracts', transformedContracts);
    setSheetRows(workbook, 'worklogs', existingWorklogs);
    writeWorkbook(workbook);

    res.json({
      success: true,
      imported_contracts: transformedContracts.length,
      added: added.length,
      changed: changed.length,
      removed: removed.length
    });
  } catch (error) {
    console.error(error);
    res.status(500).json({
      error: 'Failed to import contracts'
    });
  }
});
////////////////////////////////////////////////////////////////////

app.get('/contract-by-external-id/:externalId', (req, res) => {
  try {
    const externalId = normalizeString(req.params.externalId);
    const ticketNumber = normalizeString(req.query.ticket_number);

    if (!externalId) {
      return res.status(400).json({ error: 'externalId is required' });
    }

    const workbook = readWorkbook();
    const contracts = getSheetRows(workbook, 'contracts');
    const worklogs = getSheetRows(workbook, 'worklogs').filter(
      row => normalizeString(row.order_number)
    );

    const matchedContracts = findContractsByExternalId(contracts, externalId);

    if (matchedContracts.length === 0) {
      return res.status(404).json({
        error: `No contract found for external ID ${externalId}`
      });
    }

    const results = matchedContracts.map(contract => {
      const orderNumber = normalizeString(contract.order_number);
      const soldHours = parseNumber(contract.sold_hours);
      const bookedHoursInTicket = ticketNumber
        ? getBookedHoursForTicket(worklogs, orderNumber, ticketNumber)
        : 0;
      const usedHoursTotal = getCombinedUsedHoursTotal(contract, worklogs, orderNumber);
      const remainingHours = roundQuarter(soldHours - usedHoursTotal);
      const exceeded = usedHoursTotal >= soldHours;

      return {
        order_number: contract.order_number,
        external_id: contract.external_id,
        customer: contract.customer,
        country: contract.country,
        start: contract.start,
        valid_until: contract.valid_until,
        machine_type: contract.machine_type,
        machine_no: contract.machine_no,
        contract_type: contract.contract_type,
        sold_hours: soldHours,
        booked_hours_in_ticket: bookedHoursInTicket,
        used_hours_total: usedHoursTotal,
        remaining_hours: remainingHours,
        exceeded,
        comment: contract.comment,
        hours_by_person: ticketNumber
          ? aggregateHoursByPersonForTicket(worklogs, orderNumber, ticketNumber)
          : [],
        ticket_worklogs: ticketNumber
          ? getTicketWorklogDetails(worklogs, orderNumber, ticketNumber)
          : []
      };
    });

    if (results.length === 1) {
      return res.json(results[0]);
    }

    return res.json({
      multiple: true,
      contracts: results
    });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: 'Failed to load contract data by external ID' });
  }
});

app.get('/', (req, res) => {
  res.json({ ok: true, message: 'Excel backend is running' });
});

app.get('/contract/:orderNumber', (req, res) => {
  try {
    const orderNumber = normalizeString(req.params.orderNumber);
    const ticketNumber = normalizeString(req.query.ticket_number);

    const workbook = readWorkbook();
    const contracts = getSheetRows(workbook, 'contracts');
    const worklogs = getSheetRows(workbook, 'worklogs').filter(
      row => normalizeString(row.order_number)
    );

    const contract = contracts.find(
      row => normalizeString(row.order_number) === orderNumber
    );

    if (!contract) {
      return res.status(404).json({
        error: `No contract found for order number ${orderNumber}`
      });
    }

    const soldHours = parseNumber(contract.sold_hours);
    const bookedHoursInTicket = ticketNumber
      ? getBookedHoursForTicket(worklogs, orderNumber, ticketNumber)
      : 0;
    const usedHoursTotal = getCombinedUsedHoursTotal(contract, worklogs, orderNumber);
    const remainingHours = roundQuarter(soldHours - usedHoursTotal);
    const exceeded = usedHoursTotal >= soldHours;

    res.json({
      order_number: contract.order_number,
	  external_id: contract.external_id,
      customer: contract.customer,
      country: contract.country,
      start: contract.start,
      valid_until: contract.valid_until,
      machine_type: contract.machine_type,
      machine_no: contract.machine_no,
      contract_type: contract.contract_type,
      sold_hours: soldHours,
      booked_hours_in_ticket: bookedHoursInTicket,
      used_hours_total: usedHoursTotal,
      remaining_hours: remainingHours,
      exceeded,
      comment: contract.comment,
      hours_by_person: ticketNumber
        ? aggregateHoursByPersonForTicket(worklogs, orderNumber, ticketNumber)
        : [],
      ticket_worklogs: ticketNumber
        ? getTicketWorklogDetails(worklogs, orderNumber, ticketNumber)
        : []
    });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: 'Failed to load contract data' });
  }
});

app.post('/worklog', (req, res) => {
  try {
    const { ticket_number, order_number, customer, booked_at, entries } = req.body;

    if (!ticket_number) {
      return res.status(400).json({ error: 'ticket_number is required' });
    }

    if (!order_number) {
      return res.status(400).json({ error: 'order_number is required' });
    }

    if (!booked_at) {
      return res.status(400).json({ error: 'booked_at is required' });
    }

    if (!Array.isArray(entries) || entries.length === 0) {
      return res.status(400).json({ error: 'entries must be a non-empty array' });
    }

    const workbook = readWorkbook();
    const contracts = getSheetRows(workbook, 'contracts');
    const worklogs = getSheetRows(workbook, 'worklogs').filter(
      row => normalizeString(row.id) || normalizeString(row.order_number)
    );

    const contract = contracts.find(
      row => normalizeString(row.order_number) === normalizeString(order_number)
    );

    if (!contract) {
      return res.status(404).json({
        error: `No contract found for order number ${order_number}`
      });
    }

    let nextId = getNextWorklogId(worklogs);

    for (const entry of entries) {
      const person = normalizeString(entry.person);
      const hours = parseNumber(entry.hours);
      const comment = normalizeString(entry.comment);

      if (!person) {
        return res.status(400).json({ error: 'Each entry must have a person' });
      }

      if (hours <= 0) {
        return res.status(400).json({ error: 'Each entry must have hours > 0' });
      }

      if (Math.round(hours * 4) !== hours * 4) {
        return res.status(400).json({
          error: 'Hours must be in 0.25 steps'
        });
      }

      worklogs.push({
        id: nextId++,
        order_number: normalizeString(order_number),
        customer: normalizeString(customer || contract.customer),
        ticket_number: normalizeString(ticket_number),
        booked_at: normalizeString(booked_at),
        person,
        hours: roundQuarter(hours),
        comment
      });
    }

    setSheetRows(workbook, 'contracts', contracts);
    setSheetRows(workbook, 'worklogs', worklogs);
    writeWorkbook(workbook);

    res.json({ success: true });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: 'Failed to save worklog' });
  }
});

app.get('/worklogs/:orderNumber', (req, res) => {
  try {
    const orderNumber = normalizeString(req.params.orderNumber);
    const ticketNumber = normalizeString(req.query.ticket_number);

    const workbook = readWorkbook();
    const worklogs = getSheetRows(workbook, 'worklogs').filter(row => {
      const sameOrder =
        normalizeString(row.order_number) === normalizeString(orderNumber);

      if (!sameOrder) return false;
      if (!ticketNumber) return true;

      return normalizeString(row.ticket_number) === normalizeString(ticketNumber);
    });

    res.json(worklogs);
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: 'Failed to load worklogs' });
  }
});

app.put('/worklog/:id', (req, res) => {
  try {
    const worklogId = normalizeString(req.params.id);
    const { ticket_number, order_number, customer, person, hours, comment } = req.body;

    if (!worklogId) {
      return res.status(400).json({ error: 'worklog id is required' });
    }

    if (!ticket_number) {
      return res.status(400).json({ error: 'ticket_number is required' });
    }

    if (!order_number) {
      return res.status(400).json({ error: 'order_number is required' });
    }

    if (!person) {
      return res.status(400).json({ error: 'person is required' });
    }

    const parsedHours = parseNumber(hours);

    if (parsedHours <= 0) {
      return res.status(400).json({ error: 'hours must be > 0' });
    }

    if (Math.round(parsedHours * 4) !== parsedHours * 4) {
      return res.status(400).json({ error: 'Hours must be in 0.25 steps' });
    }

    const workbook = readWorkbook();
    const contracts = getSheetRows(workbook, 'contracts');
    const worklogs = getSheetRows(workbook, 'worklogs').filter(
      row => normalizeString(row.id) || normalizeString(row.order_number)
    );

    const index = worklogs.findIndex(
      row =>
        normalizeString(row.id) === worklogId &&
        normalizeString(row.order_number) === normalizeString(order_number) &&
        normalizeString(row.ticket_number) === normalizeString(ticket_number)
    );

    if (index === -1) {
      return res.status(404).json({ error: `No worklog found for id ${worklogId}` });
    }

    worklogs[index] = {
      ...worklogs[index],
      customer: normalizeString(customer || worklogs[index].customer),
      person: normalizeString(person),
      hours: roundQuarter(parsedHours),
      comment: normalizeString(comment)
    };

    setSheetRows(workbook, 'contracts', contracts);
    setSheetRows(workbook, 'worklogs', worklogs);
    writeWorkbook(workbook);

    res.json({ success: true, id: worklogId });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: 'Failed to update worklog' });
  }
});

app.listen(PORT, () => {
  ensureDbExists();
  console.log(`Server running on http://localhost:${PORT}`);
});