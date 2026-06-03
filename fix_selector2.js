const fs = require('fs');
const path = 'src/test/java/pages/asientos/SelectorPage.java';
let src = fs.readFileSync(path, 'utf8');

function removeMethod(text, methodName) {
    const sigs = [
        `    private List<WebElement> ${methodName}(`,
        `    private void ${methodName}(`,
        `    private boolean ${methodName}(`,
        `    private WebElement ${methodName}(`,
        `    private String ${methodName}(`,
        `    private int ${methodName}(`,
    ];
    let start = -1;
    for (const sig of sigs) {
        const pos = text.indexOf(sig);
        if (pos >= 0) { start = pos; break; }
    }
    if (start < 0) { console.log(`  ${methodName}: not found`); return text; }

    // Extend back to include javadoc
    const pre = text.substring(0, start);
    const jdEnd = pre.lastIndexOf('*/');
    if (jdEnd >= 0 && pre.substring(jdEnd + 2).trim() === '') {
        const jdStart = pre.lastIndexOf('/**', jdEnd);
        if (jdStart >= 0) {
            const nl = pre.lastIndexOf('\n', jdStart);
            start = nl >= 0 ? nl + 1 : jdStart;
        }
    } else {
        // single-line comment
        const slEnd = pre.lastIndexOf('//');
        if (slEnd >= 0 && pre.substring(slEnd).indexOf('\n') < 0) {
            const nl = pre.lastIndexOf('\n', slEnd);
            start = nl >= 0 ? nl + 1 : slEnd;
        }
    }

    let depth = 0, i = start, inMethod = false;
    while (i < text.length) {
        const c = text[i];
        if (c === '{') { depth++; inMethod = true; }
        else if (c === '}') {
            depth--;
            if (inMethod && depth === 0) {
                i++;
                if (i < text.length && text[i] === '\n') i++;
                break;
            }
        }
        i++;
    }
    console.log(`  ${methodName}: removed (${i - start} chars)`);
    return text.substring(0, start) + text.substring(i);
}

for (const m of ['obtenerAsientosDelMapaRapido', 'obtenerAsientosParaConsecutivos', 'safe']) {
    src = removeMethod(src, m);
}

fs.writeFileSync(path, src, 'utf8');
console.log('Done.');
