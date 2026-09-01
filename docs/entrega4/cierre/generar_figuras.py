"""Regenera figuras desde CSV conservados; no ejecuta experimentos."""
from pathlib import Path
import csv
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

BASE = Path(__file__).resolve().parent
with (BASE / 'experimento_crudo.csv').open(encoding='utf-8-sig') as f:
    rows = list(csv.DictReader(f))
plt.rcParams.update({'font.size': 10, 'axes.spines.top': False, 'axes.spines.right': False})
fig, axes = plt.subplots(2, 2, figsize=(11, 8), constrained_layout=True)
for ax, c in zip(axes.flat, [50, 100, 200, 400]):
    groups, labels = [], []
    for mode in ['none', 'omission', 'timing']:
        for coord in ['2pc', 'saga']:
            groups.append([float(r['latencia_pago_p95_ms']) / 1000 for r in rows if int(r['concurrencia']) == c and r['fallo'] == mode and r['coord'] == coord])
            labels.append(f'{coord.upper()}\n{mode}')
    bp = ax.boxplot(groups, tick_labels=labels, whis=(0, 100), patch_artist=True)
    for i, box in enumerate(bp['boxes']):
        box.set_facecolor('#adcce4' if i % 2 == 0 else '#edc48c')
    ax.set_title(f'Concurrencia {c} | n=5 por condición')
    ax.set_ylabel('p95 de compra por ejecución (s)')
    ax.grid(axis='y', alpha=0.2)
    ax.tick_params(axis='x', labelsize=8)
fig.savefig(BASE / 'boxplot-p95.png', dpi=220)
plt.close(fig)

with (BASE / 'tiempos_resumen.csv').open(encoding='utf-8-sig') as f:
    rows = [r for r in csv.DictReader(f) if r['etapa'] == 't1']
fig, ax = plt.subplots(figsize=(9, 5), constrained_layout=True)
for mode, label, color in [('jdbc_sin_particionar', 'Spark sin partición (n=5)', '#285e85'), ('jdbc_particionado_orden_id_4', 'Spark particionado (n=3)', '#ad6822')]:
    selected = sorted([r for r in rows if r['modalidad_lectura'] == mode], key=lambda r: float(r['ejecutores']))
    x = [float(r['ejecutores']) for r in selected]
    y = [float(r['media_segundos']) for r in selected]
    err = [[float(r['media_segundos']) - float(r['ic95_inferior']) for r in selected], [float(r['ic95_superior']) - float(r['media_segundos']) for r in selected]]
    ax.errorbar(x, y, yerr=err, marker='o', capsize=5, label=label, color=color)
pandas = next(r for r in rows if r['motor'] == 'pandas')
ax.axhline(float(pandas['media_segundos']), color='#467548', linestyle='--', label='Pandas SQL directo (n=5)')
ax.set(xticks=[1, 2, 4], xlabel='Ejecutores Spark', ylabel='Tiempo medio T1 (s)', ylim=(0, 23))
ax.grid(axis='y', alpha=0.2)
ax.legend(loc='upper left', fontsize=9)
fig.savefig(BASE / 'spark-t1.png', dpi=220)
plt.close(fig)
print('Figuras regeneradas desde datos existentes.')
