# -*- coding: utf-8 -*-
"""
Created on Mon Mar 16 16:42:17 2020

@author: 12709
"""

import cv2
net = cv2.dnn.readNetFromCaffe("deploy_age.prototxt", "age_net.caffemodel");
print(net)