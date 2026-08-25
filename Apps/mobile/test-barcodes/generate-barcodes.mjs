import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const outputDir = path.dirname(fileURLToPath(import.meta.url));
const entries = [
  ["7700000000019", "Almacenamiento"],
  ["7700000000026", "Procesador"],
  ["7700000000033", "Tarjeta gráfica"],
  ["7700000000040", "Memoria RAM"],
  ["7700000000057", "Periféricos"],
];

const leftOdd = ["0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011"];
const leftEven = ["0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111"];
const right = ["1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100"];
const parity = ["LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"];

function modules(code) {
  const digits = [...code].map(Number);
  let bits = "101";
  for (let index = 1; index <= 6; index += 1) {
    bits += parity[digits[0]][index - 1] === "L" ? leftOdd[digits[index]] : leftEven[digits[index]];
  }
  bits += "01010";
  for (let index = 7; index <= 12; index += 1) bits += right[digits[index]];
  return bits + "101";
}

function svg(code, category) {
  const bits = modules(code);
  const moduleWidth = 3;
  const quiet = 12;
  const bars = [...bits].map((bit, index) => bit === "1"
    ? `<rect x="${quiet + index * moduleWidth}" y="18" width="${moduleWidth}" height="112"/>`
    : "").join("");
  const width = quiet * 2 + bits.length * moduleWidth;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="180" viewBox="0 0 ${width} 180" role="img" aria-label="${category}: ${code}">
  <rect width="100%" height="100%" fill="white"/>
  <g fill="black">${bars}</g>
  <text x="${width / 2}" y="150" text-anchor="middle" font-family="monospace" font-size="18">${code}</text>
  <text x="${width / 2}" y="172" text-anchor="middle" font-family="sans-serif" font-size="15">${category}</text>
</svg>\n`;
}

const cards = [];
for (const [code, category] of entries) {
  const filename = `${code}-${category.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-zA-Z0-9]+/g, "-").toLowerCase()}.svg`;
  fs.writeFileSync(path.join(outputDir, filename), svg(code, category));
  cards.push(`<figure><img src="${filename}" alt="${category}: ${code}"><figcaption>${category}</figcaption></figure>`);
}

const html = `<!doctype html><html lang="es"><meta charset="utf-8"><title>Códigos TiendaTech</title>
<style>body{font-family:sans-serif;margin:24px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:20px}figure{margin:0;padding:18px;border:1px solid #bbb;border-radius:12px;text-align:center}img{width:100%;max-width:360px}figcaption{font-weight:700;margin-top:8px}@media print{figure{break-inside:avoid}}</style>
<h1>Códigos de prueba TiendaTech</h1><p>Abre cada código en otra pantalla o imprime esta hoja y escanéalo desde la aplicación.</p><main>${cards.join("")}</main></html>\n`;
fs.writeFileSync(path.join(outputDir, "codigos-prueba.html"), html);
