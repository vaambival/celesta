from g1._g1_orm import aaCursor 

def hello(conn, arg):
    print 'Hello, world from Celesta Python procedure.'
    print 'Argument passed was "%s".' % arg
    aa = aaCursor(conn)
    aa.deleteAll()
    for i in range(1, 12):
        aa.idaa = i
        aa.idc = i * i
        aa.insert()
   
    while aa.next():
        print "%s : %s" % (aa.idaa , aa.idc)
    print 'Python procedure finished.'