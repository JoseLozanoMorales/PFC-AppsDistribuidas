FROM apache/spark:3.5.5-python3

USER root
# pandas/scipy/matplotlib/pyarrow sin version fija: la imagen base trae
# Python 3.8, y las versiones pineadas en spark/requirements.txt (pensado
# para un host con Python 3.11+) ya no publican wheels para 3.8. Se deja que
# pip resuelva la ultima version compatible con 3.8.
RUN pip3 install --no-cache-dir \
    numpy==1.24.4 \
    psutil==6.1.1 \
    pandas \
    scipy \
    matplotlib \
    "psycopg[binary]" \
    pyarrow
USER 185
