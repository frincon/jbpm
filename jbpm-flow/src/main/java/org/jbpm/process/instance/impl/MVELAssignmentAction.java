package org.jbpm.process.instance.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import org.drools.base.mvel.MVELCompilationUnit;
import org.drools.base.mvel.MVELCompileable;
import org.drools.common.InternalWorkingMemory;
import org.drools.definition.KnowledgePackage;
import org.drools.definitions.impl.KnowledgePackageImp;
import org.drools.impl.StatefulKnowledgeSessionImpl;
import org.drools.impl.StatelessKnowledgeSessionImpl;
import org.drools.process.instance.WorkItem;
import org.drools.rule.MVELDialectRuntimeData;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.StatelessKnowledgeSession;
import org.drools.runtime.process.ProcessContext;
import org.drools.spi.GlobalResolver;
import org.jbpm.workflow.core.node.Assignment;
import org.mvel2.MVEL;
import org.mvel2.integration.VariableResolverFactory;

public class MVELAssignmentAction implements AssignmentAction, MVELCompileable, Externalizable {
	private static final long serialVersionUID = 510l;

	private MVELCompilationUnit unit;
	private String id;
	private String targetExpr;
	private boolean isInput;

	private Serializable expr;

	public MVELAssignmentAction(final MVELCompilationUnit unit, final String id, Assignment assignment, String sourceExpr, String targetExpr, boolean isInput) {
		if (!isInput) {
			throw new UnsupportedOperationException("MVEL Assignments only support input assignments");
		}
		this.unit = unit;
		this.id = id;
		this.targetExpr = targetExpr;
		this.isInput = isInput;
	}

	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		id = in.readUTF();
		unit = (MVELCompilationUnit) in.readObject();
		targetExpr = in.readUTF();
		isInput = in.readBoolean();
	}

	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(id);
		out.writeObject(unit);
		out.writeUTF(targetExpr);
		out.writeBoolean(isInput);
	}

	public void compile(MVELDialectRuntimeData data) {
		expr = unit.getCompiledExpression(data);
	}

	public String getDialect() {
		return this.id;
	}

	public Object evaluate(ProcessContext context) throws Exception {
		int length = unit.getOtherIdentifiers().length;
		Object[] vars = new Object[length];
		if (unit.getOtherIdentifiers() != null) {
			for (int i = 0; i < length; i++) {
				vars[i] = context.getVariable(unit.getOtherIdentifiers()[i]);
			}
		}

		InternalWorkingMemory internalWorkingMemory = null;
		if (context.getKnowledgeRuntime() instanceof StatefulKnowledgeSessionImpl) {
			internalWorkingMemory = ((StatefulKnowledgeSessionImpl) context.getKnowledgeRuntime()).session;
		} else if (context.getKnowledgeRuntime() instanceof StatelessKnowledgeSession) {
			StatefulKnowledgeSession statefulKnowledgeSession = ((StatelessKnowledgeSessionImpl) context.getKnowledgeRuntime())
					.newWorkingMemory();
			internalWorkingMemory = ((StatefulKnowledgeSessionImpl) statefulKnowledgeSession).session;
		}

		VariableResolverFactory factory = unit.getFactory(context, null, // No previous declarations
				null, // No rule
				null, // No "right object" 
				null, // No (left) tuples
				vars, internalWorkingMemory, (GlobalResolver) context.getKnowledgeRuntime().getGlobals());

		// do we have any functions for this namespace?
		KnowledgePackage pkg = context.getKnowledgeRuntime().getKnowledgeBase().getKnowledgePackage("MAIN");
		if (pkg != null && pkg instanceof KnowledgePackageImp) {
			MVELDialectRuntimeData data = (MVELDialectRuntimeData) ((KnowledgePackageImp) pkg).pkg.getDialectRuntimeRegistry()
					.getDialectData(id);
			factory.setNextFactory(data.getFunctionFactory());
		}

		Object value = MVEL.executeExpression(this.expr, null, factory);

		return value;

	}

	public Serializable getCompExpr() {
		return expr;
	}

	public String toString() {
		return this.unit.getExpression();
	}

	public void execute(WorkItem workItem, ProcessContext context) throws Exception {
		Object value = evaluate(context);
		workItem.setParameter(targetExpr, value);
	}

}
