package com.jazzpirate.gclient.ui

import java.awt.FileDialog
import java.awt.event.{ActionEvent, ActionListener}
import java.beans.{PropertyChangeListener, Transient}

import com.jazzpirate.gclient.{Mount, Settings}
import com.jazzpirate.gclient.hosts.{Account, CloudDirectory}
import com.jazzpirate.gclient.ui.Main.mainPanel
import info.kwarc.mmt.api.utils.File
import javax.swing.event.{TreeExpansionEvent, TreeSelectionEvent, TreeSelectionListener, TreeWillExpandListener}
import javax.swing.{JFrame, JOptionPane, JTree, SwingUtilities, SwingWorker}
import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel}

class AccountForm(acc:Account) extends AccountJava {
  account_name.setText(acc.account_name)
  treePane.remove(folder_tree)
  val root = new FolderNode(1, 0,acc)
  val model = new DefaultTreeModel(root)

  folder_tree = new JTree() {
    @Transient
    override def getPreferredSize = {
      val preferredSize = super.getPreferredSize
      preferredSize.width = Math.max(400, preferredSize.width);
      preferredSize.height = Math.max(400, preferredSize.height);
      preferredSize
    }
  }
  folder_tree.setShowsRootHandles(true);
  folder_tree.addTreeWillExpandListener(new TreeWillExpandListener() {
    override def treeWillExpand(event: TreeExpansionEvent): Unit = {
      val path = event.getPath
      path.getLastPathComponent match {
        case fn:FolderNode =>
          fn.loadChildren(model)
        case _ =>
      }
    }

    override def treeWillCollapse(event: TreeExpansionEvent): Unit = {}
  })
  folder_tree.setModel(model)
  root.loadChildren(model)
  treePane.setViewportView(folder_tree)
  folder_tree.addTreeSelectionListener(new TreeSelectionListener {
    override def valueChanged(e: TreeSelectionEvent): Unit = {
      btn_mount.setEnabled(true)
      // btn_sync.setEnabled(true)
    }
  })

  btn_mount.addActionListener(new ActionListener {
    def actionPerformed(e:ActionEvent) = {
      val cloud_path = folder_tree.getSelectionPath.getLastPathComponent.asInstanceOf[FolderNode].path
      import javax.swing.JFileChooser
      val chooser = new JFileChooser
      // chooser.setCurrentDirectory(new File("."))
      chooser.setDialogTitle("Choose empty folder")
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
      chooser.setAcceptAllFileFilterUsed(false)

      if (chooser.showOpenDialog(treePane) == JFileChooser.APPROVE_OPTION) {
        val dir = File(chooser.getSelectedFile)
        if (dir.children.isEmpty) {
          Settings.settings.addSync(Mount(acc.account_name, dir, cloud_path.toList))
          Main.getBack(None)
        } else {
          JOptionPane.showMessageDialog(mainPanel,"Directory must be empty!","Error",0)
        }
      }
      /*
      val diag = new FileDialog(SwingUtilities.getWindowAncestor(treePane).asInstanceOf[JFrame])
      diag.setVisible(true)
      while (diag.isVisible) {
        Thread.sleep(100)
      }
      if (diag.get)
      val file = File(diag.getFile)
      val dir = if (file.isDirectory) file else file.up

       */
    }
  })
}

class FolderNode(index:Int,depth:Int,acc:Account,val path:String*) extends DefaultMutableTreeNode {
  private var loaded = false
  lazy val dir = acc.getFile(path:_*) match {
    case cf:CloudDirectory => cf
    case _ =>
      ???
  }

  override def toString: String = path.lastOption.getOrElse("/")

  add(new DefaultMutableTreeNode("Loading...", false))
  setAllowsChildren(true);
  setUserObject("Child " + index + " at level " + depth)

  private def setChildren(children: List[FolderNode]) {
    removeAllChildren()
    setAllowsChildren(children.nonEmpty)
    children.foreach(add)
    loaded = true
  }

  override def isLeaf = false

  val self = this

  def loadChildren(model: DefaultTreeModel): Unit = if (!loaded) {
    val worker = new SwingWorker[List[FolderNode], Unit] {
      override def doInBackground: List[FolderNode] = {
        var i = -1
        val ret = dir.children.collect {
          case d: CloudDirectory =>
            i += 1
            new FolderNode(i, depth + 1, acc, path.toList ::: List(d.name): _*)
        }
        ret.foreach(children.add)
        ret
      }

      override def done(): Unit = {
        setChildren(get());
        model.nodeStructureChanged(self);
        super.done()
      }
    }
    worker.execute()

  }
}
/*
  protected void initUI() {
    JFrame frame = new JFrame(TestJTree.class.getSimpleName());
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    MyTreeNode root = new MyTreeNode(1, 0);
    final DefaultTreeModel model = new DefaultTreeModel(root);
    final JProgressBar bar = new JProgressBar();
    final PropertyChangeListener progressListener = new PropertyChangeListener() {

      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        bar.setValue((Integer) evt.getNewValue());
      }
    };
    JTree tree = new JTree() {
      @Override
      @Transient
      public Dimension getPreferredSize() {
        Dimension preferredSize = super.getPreferredSize();
        preferredSize.width = Math.max(400, preferredSize.width);
        preferredSize.height = Math.max(400, preferredSize.height);
        return preferredSize;
      }
    };
    tree.setShowsRootHandles(true);
    tree.addTreeWillExpandListener(new TreeWillExpandListener() {

      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        TreePath path = event.getPath();
        if (path.getLastPathComponent() instanceof MyTreeNode) {
          MyTreeNode node = (MyTreeNode) path.getLastPathComponent();
          node.loadChildren(model, progressListener);
        }
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {

      }
    });
    tree.setModel(model);
    root.loadChildren(model, progressListener);
    frame.add(new JScrollPane(tree));
    frame.add(bar, BorderLayout.SOUTH);
    frame.pack();
    frame.setVisible(true);
  }

  public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException,
  UnsupportedLookAndFeelException {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    SwingUtilities.invokeLater(new Runnable() {

      @Override
      public void run() {
        new TestJTree().initUI();
      }
    });
  }
}

 */