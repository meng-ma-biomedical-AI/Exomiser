INSERT INTO orphanet SELECT * FROM CSVREAD('${import.path}/orphanet.pg', 'orphanumber|entrezgeneid|diseasename','charset=UTF-8 fieldDelimiter='' fieldSeparator=| nullString=NULL');