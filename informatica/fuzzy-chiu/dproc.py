#! /usr/bin/python
# coding: utf-8

import numpy as np
import sys

L = 1   # Normalize to interval [-L, L]

def print_data(data):
    """Print data in the same format as the input data."""

    nrows, ncols = data.shape
    for i in range(nrows):
        for j in range(ncols):
            print(str(data[i, j]) + ("," if j != ncols - 1 else ""), end = "")
        print("")

def compute_range(data, ncols):
    """ Compute min and max values in every dimension."""

    min_cols = [data[:,j].min() for j in range(ncols)]
    max_cols = [data[:,j].max() for j in range(ncols)]

    return np.array([min_cols, max_cols])

def norm(data, nrows, ncols, range_cols):
    """Normalize data to [-L, L] from original values."""

    for j in range(ncols):
        min = range_cols[0,j]
        max = range_cols[1,j]

        for i in range(nrows):
            data[i, j] = - L + ((data[i, j] - min) * (2 * L)) / (max - min)

def denorm(data, nrows, ncols, range_cols):
    """Denormalize data from [-L, L] to original values."""

    for j in range(ncols):
        min = range_cols[0,j]
        max = range_cols[1,j]

        for i in range(nrows):
    	    data[i, j] = min + ((data[i, j] + L) * (max - min)) / (2 * L)

def main():
    if (len(sys.argv) < 3):
        print("uso: ./dproc.py [--norm, --denorm, --range] DATA [DATA_RANGE]")
        sys.exit(1)

    data = np.genfromtxt(sys.argv[2], delimiter=',')
    nrows, ncols = data.shape

    if (sys.argv[1] == "--norm"):
        if (len(sys.argv) < 4):
            print("error: para normalizar debe proporcionarse un archivo con el rango de los datos en cada columna.")

        range_cols = np.genfromtxt(sys.argv[3], delimiter=',')
        norm(data, nrows, ncols, range_cols)
        print_data(data)

    elif (sys.argv[1] == "--denorm"):
        if (len(sys.argv) < 4):
            print("error: para normalizar debe proporcionarse un archivo con el rango de los datos en cada columna.")

        range_cols = np.genfromtxt(sys.argv[3], delimiter=',')
        denorm(data, nrows, ncols, range_cols)
        print_data(data)

    elif (sys.argv[1] == "--range"):
        print_data(compute_range(data, ncols))

    else:
        print("uso: ./dproc.py [--norm, --denorm, --range] DATA [DATA_RANGE]")

if __name__ == "__main__":
    main()
